package com.totemena.elite.game;

import com.totemena.elite.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameScheduler {

    private final StringRedisTemplate redisTemplate;
    private final RoundRepository roundRepository;
    private final BetRepository betRepository;
    private final GameBroadcastService gameBroadcastService;
    private final WalletService walletService;
    private final GamePayoutService gamePayoutService;

    private final SecureRandom random = new SecureRandom();

    @Value("${app.game.betting-phase-seconds:15}")
    private int bettingPhaseSeconds;

    @Value("${app.game.spinning-phase-seconds:7}")
    private int spinningPhaseSeconds;
    
    @Value("${app.game.result-display-seconds:3}")
    private int resultDisplaySeconds;
    
    @Value("${app.game.multiplier:2.00}")
    private java.math.BigDecimal multiplier;

    private long lastTickSecond = -1;

    @Scheduled(fixedRate = 200)
    public void tick() {
        String roundIdStr = redisTemplate.opsForValue().get("game:current_round_id");

        if (roundIdStr == null) {
            startNewRound();
            return;
        }

        String phase = redisTemplate.opsForValue().get("game:round:" + roundIdStr + ":phase");
        String startedAtStr = redisTemplate.opsForValue().get("game:round:" + roundIdStr + ":started_at");
        
        if (phase == null || startedAtStr == null) {
            log.warn("Round state is missing in Redis. Deleting current round and starting a new one.");
            redisTemplate.delete("game:current_round_id");
            startNewRound();
            return;
        }

        long startedAt = Long.parseLong(startedAtStr);
        long elapsedMs = System.currentTimeMillis() - startedAt;
        long elapsedSeconds = elapsedMs / 1000;
        
        // Phase transitions FIRST — before broadcasting the tick
        // This ensures the frontend never sees remaining=0 with an old phase
        if ("BETTING".equals(phase) && elapsedMs >= bettingPhaseSeconds * 1000L) {
            transitionToSpinning(roundIdStr);
            return;
        } else if ("SPINNING".equals(phase)) {
            // Secretly send the result 1.6s early so frontend stop animation fits perfectly
            long revealMs = (spinningPhaseSeconds * 1000L) - 1600L;
            if (elapsedMs >= revealMs) {
                String resultSentKey = "game:round:" + roundIdStr + ":result_sent";
                if (Boolean.FALSE.equals(redisTemplate.hasKey(resultSentKey))) {
                    sendResultEarly(roundIdStr);
                    redisTemplate.opsForValue().set(resultSentKey, "true", 120, TimeUnit.SECONDS);
                }
            }

            if (elapsedMs >= spinningPhaseSeconds * 1000L) {
                finishRound(roundIdStr);
                return;
            }
        } else if ("FINISHED".equals(phase) && elapsedMs >= 2500L) {
            redisTemplate.delete("game:current_round_id");
            startNewRound();
            return;
        }

        // Only broadcast tick once per second if we're still in the same phase
        if (elapsedSeconds != lastTickSecond) {
            lastTickSecond = elapsedSeconds;
            int remaining = calculateRemaining(phase, elapsedSeconds);
            gameBroadcastService.broadcastTick(roundIdStr, phase, remaining);
        }
    }

    private void sendResultEarly(String roundIdStr) {
        UUID roundId = UUID.fromString(roundIdStr);
        roundRepository.findById(roundId).ifPresent(round -> {
            gameBroadcastService.broadcastResult(roundIdStr, round.getWinningRowTota(), round.getWinningRowMena());
        });
    }

    private int calculateRemaining(String phase, long elapsedSeconds) {
        return switch (phase) {
            case "BETTING"  -> Math.max(0, bettingPhaseSeconds  - (int) elapsedSeconds) + spinningPhaseSeconds;
            case "SPINNING" -> Math.max(0, spinningPhaseSeconds - (int) elapsedSeconds);
            default         -> 0;
        };
    }

    @Transactional
    protected void startNewRound() {
        Round newRound = Round.builder().status("BETTING").build();
        roundRepository.save(newRound);
        String roundId = newRound.getId().toString();

        redisTemplate.opsForValue().set("game:current_round_id", roundId);
        redisTemplate.opsForValue().set("game:round:" + roundId + ":phase", "BETTING", 120, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set("game:round:" + roundId + ":started_at", String.valueOf(System.currentTimeMillis()), 120, TimeUnit.SECONDS);

        gameBroadcastService.broadcastPhaseChange(roundId, "BETTING");
        log.info("Started new round: {}", roundId);
    }

    @Transactional
    protected void transitionToSpinning(String roundIdStr) {
        UUID roundId = UUID.fromString(roundIdStr);
        Round round = roundRepository.findById(roundId).orElseThrow();

        if (!"BETTING".equals(round.getStatus())) {
            return;
        }

        short winTota = (short) (random.nextInt(5) + 1);
        short winMena = (short) (random.nextInt(5) + 1);

        round.setStatus("SPINNING");
        round.setWinningRowTota(winTota);
        round.setWinningRowMena(winMena);
        round.setSpinningAt(Instant.now());
        roundRepository.save(round);

        redisTemplate.opsForValue().set("game:round:" + roundIdStr + ":phase", "SPINNING");
        redisTemplate.opsForValue().set("game:round:" + roundIdStr + ":started_at", String.valueOf(System.currentTimeMillis()));

        gameBroadcastService.broadcastPhaseChange(roundIdStr, "SPINNING");
        log.info("Round {} entered SPINNING phase", roundIdStr);
    }

    @Transactional
    protected void finishRound(String roundIdStr) {
        UUID roundId = UUID.fromString(roundIdStr);
        Round round = roundRepository.findById(roundId).orElseThrow();

        if (!"SPINNING".equals(round.getStatus())) return;

        // Grab values before async — don't pass the managed entity
        short winTota = round.getWinningRowTota();
        short winMena = round.getWinningRowMena();

        // Mark round FINISHED immediately — this is the fast path
        round.setStatus("FINISHED");
        round.setFinishedAt(Instant.now());
        roundRepository.save(round);

        // Update Redis immediately so new round can start
        redisTemplate.opsForValue().set("game:round:" + roundIdStr + ":phase", "FINISHED");
        redisTemplate.opsForValue().set("game:round:" + roundIdStr + ":started_at",
            String.valueOf(System.currentTimeMillis()));

        // Broadcast phase NOW — before payout processing
        gameBroadcastService.broadcastPhaseChange(roundIdStr, "FINISHED");

        log.info("Round {} FINISHED — winTota={} winMena={}", roundIdStr, winTota, winMena);

        // Process payouts asynchronously — does not block the scheduler
        gamePayoutService.processPayoutsAsync(roundId, winTota, winMena, roundIdStr);
    }
}
