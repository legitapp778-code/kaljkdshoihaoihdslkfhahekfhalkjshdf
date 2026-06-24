package com.totemena.elite.game;

import com.totemena.elite.common.exception.BettingClosedException;
import com.totemena.elite.game.dto.BetAckEvent;
import com.totemena.elite.game.dto.BetResultResponse;
import com.totemena.elite.game.dto.PlaceBetRequest;
import com.totemena.elite.user.User;
import com.totemena.elite.wallet.WalletService;
import com.totemena.elite.wallet.WalletTransaction;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.util.StopWatch;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameService {

    private final BetRepository betRepository;
    private final RoundRepository roundRepository;
    private final WalletService walletService;
    private final GameBroadcastService gameBroadcastService;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final GameScheduler gameScheduler;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private GameService self;

    public BetResultResponse placeBet(User user, PlaceBetRequest request) {
        long t0 = System.currentTimeMillis();
        String currentRoundIdStr = gameScheduler.getCachedRoundId();
        if (currentRoundIdStr == null) {
            throw new BettingClosedException("Game is not active");
        }

        String phase = gameScheduler.getCachedPhase();
        if (!"BETTING".equals(phase)) {
            throw new BettingClosedException("Betting is closed for this round");
        }

        UUID roundId = UUID.fromString(currentRoundIdStr);
        long t1 = System.currentTimeMillis();
        log.info("--- PLACE BET TIMING: Phase check took {}ms", (t1 - t0));

        if (request.getIdempotencyKey() != null) {
            java.util.Optional<Bet> idemMatch = betRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (idemMatch.isPresent()) {
                log.info("--- PLACE BET TIMING: Idempotency hit took {}ms", (System.currentTimeMillis() - t1));
                return buildResponse(idemMatch.get(), walletService.getBalance(user.getId()));
            }
        }
        long t2 = System.currentTimeMillis();
        log.info("--- PLACE BET TIMING: Idempotency check took {}ms", (t2 - t1));

        Bet existingBetForBird = betRepository.findByRoundIdAndUserIdAndBird(roundId, user.getId(), request.getBird()).orElse(null);
        long t3 = System.currentTimeMillis();
        log.info("--- PLACE BET TIMING: Existing bet check took {}ms", (t3 - t2));

        if (existingBetForBird == null) {
            BetResultResponse res = self.placeNewBet(user, request, roundId);
            log.info("--- PLACE BET TIMING: placeNewBet took {}ms", (System.currentTimeMillis() - t3));
            return res;
        } else {
            BetResultResponse res = self.updateExistingBet(user, request, existingBetForBird);
            log.info("--- PLACE BET TIMING: updateExistingBet took {}ms", (System.currentTimeMillis() - t3));
            return res;
        }
    }

    @Transactional
    public BetResultResponse placeNewBet(User user, PlaceBetRequest request, UUID roundId) {
        long t0 = System.currentTimeMillis();
        WalletTransaction tx = walletService.deductFunds(
            user.getId(), request.getAmountPaise(), "BET_PLACED", null);
        long t1 = System.currentTimeMillis();
        log.info("--- PLACE BET TIMING (placeNewBet): deductFunds took {}ms", (t1 - t0));

        Round round = roundRepository.getReferenceById(roundId);
        Bet bet = Bet.builder()
                .round(round)
                .user(user)
                .bird(request.getBird())
                .selectedRow(request.getSelectedRow())
                .amountPaise(request.getAmountPaise())
                .idempotencyKey(request.getIdempotencyKey())
                .status("PENDING")
                .build();
                
        bet = betRepository.save(bet);
        long t2 = System.currentTimeMillis();
        log.info("--- PLACE BET TIMING (placeNewBet): betRepository.save took {}ms", (t2 - t1));

        // Let Hibernate manage the association in memory. No extra database roundtrip!
        tx.setReferenceId(bet.getId());
        long t3 = System.currentTimeMillis();
        log.info("--- PLACE BET TIMING (placeNewBet): updateTransactionReference took {}ms", (t3 - t2));

        BetAckEvent ack = new BetAckEvent(
            bet.getId().toString(), bet.getBird(), bet.getSelectedRow(),
            bet.getAmountPaise(), tx.getBalanceAfterPaise(),
            request.getIdempotencyKey() != null ? request.getIdempotencyKey().toString() : null
        );
        gameBroadcastService.sendBetAck(user.getId(), ack);
        long t4 = System.currentTimeMillis();
        log.info("--- PLACE BET TIMING (placeNewBet): sendBetAck took {}ms", (t4 - t3));
        return buildResponse(bet, tx.getBalanceAfterPaise());
    }

    @Transactional
    public BetResultResponse updateExistingBet(User user, PlaceBetRequest request, Bet existing) {
        long diff = request.getAmountPaise() - existing.getAmountPaise();
        long finalBalance;

        if (diff > 0) {
            WalletTransaction tx = walletService.deductFunds(
                user.getId(), diff, "BET_UPDATED", existing.getId());
            finalBalance = tx.getBalanceAfterPaise();
        } else if (diff < 0) {
            WalletTransaction tx = walletService.addFunds(
                user.getId(), Math.abs(diff), "BET_REFUND", existing.getId());
            finalBalance = tx.getBalanceAfterPaise();
        } else {
            finalBalance = walletService.getBalance(user.getId());
        }

        betRepository.updateBetRowAndAmount(
            existing.getId(),
            request.getSelectedRow(),
            request.getAmountPaise(),
            request.getIdempotencyKey()
        );
        existing.setSelectedRow(request.getSelectedRow());
        existing.setAmountPaise(request.getAmountPaise());

        BetAckEvent ack = new BetAckEvent(
            existing.getId().toString(), existing.getBird(),
            request.getSelectedRow(), request.getAmountPaise(),
            finalBalance,
            request.getIdempotencyKey() != null ? request.getIdempotencyKey().toString() : null
        );
        gameBroadcastService.sendBetAck(user.getId(), ack);
        return buildResponse(existing, finalBalance);
    }

    @Transactional
    public long cancelBet(User user, String bird) {
        String currentRoundIdStr = gameScheduler.getCachedRoundId();
        if (currentRoundIdStr == null) {
            throw new BettingClosedException("Game is not active");
        }
        if (!"BETTING".equals(gameScheduler.getCachedPhase())) {
            throw new BettingClosedException("Betting is closed — cannot cancel now");
        }

        UUID roundId = UUID.fromString(currentRoundIdStr);
        Bet bet = betRepository.findByRoundIdAndUserIdAndBird(roundId, user.getId(), bird)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "No active bet found for " + bird));

        if (!"PENDING".equals(bet.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bet already resolved");
        }

        long refund = bet.getAmountPaise();
        WalletTransaction tx = walletService.addFunds(
            user.getId(), refund, "BET_CANCELLED", bet.getId());
        betRepository.delete(bet);

        gameBroadcastService.sendBalanceUpdate(
            user.getId(), tx.getBalanceAfterPaise(),
            refund, currentRoundIdStr, "BET_CANCELLED", null);

        return tx.getBalanceAfterPaise();
    }

    private BetResultResponse buildResponse(Bet bet, long balance) {
        return BetResultResponse.builder()
            .betId(bet.getId())
            .bird(bet.getBird())
            .selectedRow(bet.getSelectedRow())
            .amountPaise(bet.getAmountPaise())
            .balanceAfterPaise(balance)
            .build();
    }
}
