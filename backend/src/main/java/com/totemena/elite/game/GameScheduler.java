package com.totemena.elite.game;

import com.totemena.elite.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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

    // In-memory cache to eliminate Redis GET operations from the 200ms tick
    private String cachedRoundId = null;
    private String cachedPhase = null;
    private Long cachedStartedAt = null;
    private Short cachedWinTota = null;
    private Short cachedWinMena = null;

    public String getCachedRoundId() { return cachedRoundId; }
    public String getCachedPhase() { return cachedPhase; }

    private void clearCacheAndRedis() {
        redisTemplate.delete("game:current_round_id");
        cachedRoundId = null;
        cachedPhase = null;
        cachedStartedAt = null;
        cachedWinTota = null;
        cachedWinMena = null;
    }

    @Scheduled(fixedRate = 200)
    public void tick() {
        if (cachedRoundId == null) {
            cachedRoundId = redisTemplate.opsForValue().get("game:current_round_id");
            if (cachedRoundId == null) {
                startNewRound();
                return;
            }
            cachedPhase = redisTemplate.opsForValue().get("game:round:" + cachedRoundId + ":phase");
            String startedStr = redisTemplate.opsForValue().get("game:round:" + cachedRoundId + ":started_at");
            cachedStartedAt = startedStr != null ? Long.parseLong(startedStr) : null;
            
            String winTotaStr = redisTemplate.opsForValue().get("game:round:" + cachedRoundId + ":win_tota");
            String winMenaStr = redisTemplate.opsForValue().get("game:round:" + cachedRoundId + ":win_mena");
            if (winTotaStr != null) cachedWinTota = Short.parseShort(winTotaStr);
            if (winMenaStr != null) cachedWinMena = Short.parseShort(winMenaStr);
        }

        if (cachedPhase == null || cachedStartedAt == null) {
            log.warn("Round state is missing in Redis. Deleting current round and starting a new one.");
            clearCacheAndRedis();
            startNewRound();
            return;
        }

        long elapsedMs = System.currentTimeMillis() - cachedStartedAt;
        long elapsedSeconds = elapsedMs / 1000;
        
        if ("BETTING".equals(cachedPhase) && elapsedMs >= bettingPhaseSeconds * 1000L) {
            transitionToSpinning();
            return;
        } else if ("SPINNING".equals(cachedPhase)) {
            long revealMs = (spinningPhaseSeconds * 1000L) - 1600L;
            if (elapsedMs >= revealMs) {
                String resultSentKey = "game:round:" + cachedRoundId + ":result_sent";
                if (Boolean.FALSE.equals(redisTemplate.hasKey(resultSentKey))) {
                    sendResultEarly(cachedRoundId);
                    redisTemplate.opsForValue().set(resultSentKey, "true", 120, TimeUnit.SECONDS);
                }
            }

            if (elapsedMs >= spinningPhaseSeconds * 1000L) {
                finishRound();
                return;
            }
        } else if ("FINISHED".equals(cachedPhase) && elapsedMs >= 2500L) {
            clearCacheAndRedis();
            startNewRound();
            return;
        }

        if (elapsedSeconds != lastTickSecond) {
            lastTickSecond = elapsedSeconds;
            int remaining = calculateRemaining(cachedPhase, elapsedSeconds);
            gameBroadcastService.broadcastTick(cachedRoundId, cachedPhase, remaining);
        }
    }

    private void sendResultEarly(String roundIdStr) {
        if (cachedWinTota != null && cachedWinMena != null) {
            gameBroadcastService.broadcastResult(roundIdStr, cachedWinTota, cachedWinMena);
        }
    }

    private int calculateRemaining(String phase, long elapsedSeconds) {
        return switch (phase) {
            case "BETTING"  -> Math.max(0, bettingPhaseSeconds  - (int) elapsedSeconds) + spinningPhaseSeconds;
            case "SPINNING" -> Math.max(0, spinningPhaseSeconds - (int) elapsedSeconds);
            default         -> 0;
        };
    }

    protected void startNewRound() {
        Round newRound = Round.builder().status("BETTING").build();
        roundRepository.save(newRound);
        String roundId = newRound.getId().toString();

        cachedRoundId = roundId;
        cachedPhase = "BETTING";
        cachedStartedAt = System.currentTimeMillis();

        redisTemplate.opsForValue().set("game:current_round_id", roundId);
        redisTemplate.opsForValue().set("game:round:" + roundId + ":phase", cachedPhase, 120, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set("game:round:" + roundId + ":started_at", String.valueOf(cachedStartedAt), 120, TimeUnit.SECONDS);

        gameBroadcastService.broadcastPhaseChange(roundId, cachedPhase);
        log.info("Started new round: {}", roundId);
    }

    protected void transitionToSpinning() {
        String roundIdStr = cachedRoundId;
        cachedPhase = "SPINNING";
        cachedStartedAt = System.currentTimeMillis();
        
        short winTota = (short) (random.nextInt(5) + 1);
        short winMena = (short) (random.nextInt(5) + 1);
        cachedWinTota = winTota;
        cachedWinMena = winMena;

        redisTemplate.opsForValue().set("game:round:" + roundIdStr + ":phase", cachedPhase, 120, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set("game:round:" + roundIdStr + ":started_at", String.valueOf(cachedStartedAt), 120, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set("game:round:" + roundIdStr + ":win_tota", String.valueOf(winTota), 120, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set("game:round:" + roundIdStr + ":win_mena", String.valueOf(winMena), 120, TimeUnit.SECONDS);
        
        gameBroadcastService.broadcastPhaseChange(roundIdStr, cachedPhase);
        log.info("Round {} entered SPINNING phase", roundIdStr);

        CompletableFuture.runAsync(() -> {
            try {
                UUID roundId = UUID.fromString(roundIdStr);
                Round round = roundRepository.findById(roundId).orElse(null);
                if (round != null && "BETTING".equals(round.getStatus())) {
                    round.setStatus("SPINNING");
                    round.setWinningRowTota(winTota);
                    round.setWinningRowMena(winMena);
                    round.setSpinningAt(Instant.now());
                    roundRepository.save(round);
                }
            } catch (Exception e) {
                log.error("Async transitionToSpinning DB update failed", e);
            }
        });
    }

    protected void finishRound() {
        String roundIdStr = cachedRoundId;
        cachedPhase = "FINISHED";
        cachedStartedAt = System.currentTimeMillis();

        redisTemplate.opsForValue().set("game:round:" + roundIdStr + ":phase", cachedPhase, 120, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set("game:round:" + roundIdStr + ":started_at", String.valueOf(cachedStartedAt), 120, TimeUnit.SECONDS);
        gameBroadcastService.broadcastPhaseChange(roundIdStr, cachedPhase);

        Short winTota = cachedWinTota;
        Short winMena = cachedWinMena;
        
        log.info("Round {} FINISHED — winTota={} winMena={}", roundIdStr, winTota, winMena);

        CompletableFuture.runAsync(() -> {
            try {
                UUID roundId = UUID.fromString(roundIdStr);
                Round round = roundRepository.findById(roundId).orElse(null);
                if (round != null && "SPINNING".equals(round.getStatus())) {
                    round.setStatus("FINISHED");
                    round.setFinishedAt(Instant.now());
                    roundRepository.save(round);
                }
                if (winTota != null && winMena != null) {
                    gamePayoutService.processPayoutsAsync(roundId, winTota, winMena, roundIdStr);
                }
            } catch (Exception e) {
                log.error("Async finishRound DB update failed", e);
            }
        });
    }
}
