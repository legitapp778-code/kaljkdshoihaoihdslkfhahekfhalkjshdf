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
        String currentRoundIdStr = gameScheduler.getCachedRoundId();
        if (currentRoundIdStr == null) {
            throw new BettingClosedException("Game is not active");
        }

        String phase = gameScheduler.getCachedPhase();
        if (!"BETTING".equals(phase)) {
            throw new BettingClosedException("Betting is closed for this round");
        }

        UUID roundId = UUID.fromString(currentRoundIdStr);

        if (request.getIdempotencyKey() != null) {
            java.util.Optional<Bet> idemMatch = betRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (idemMatch.isPresent()) {
                return buildResponse(idemMatch.get(), walletService.getBalance(user.getId()));
            }
        }

        Bet existingBetForBird = betRepository.findByRoundIdAndUserIdAndBird(roundId, user.getId(), request.getBird()).orElse(null);

        if (existingBetForBird == null) {
            return self.placeNewBet(user, request, roundId);
        } else {
            return self.updateExistingBet(user, request, existingBetForBird);
        }
    }

    @Transactional
    public BetResultResponse placeNewBet(User user, PlaceBetRequest request, UUID roundId) {
        WalletTransaction tx = walletService.deductFunds(
            user.getId(), request.getAmountPaise(), "BET_PLACED", null);

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

        // We can cast TransactionRepository or inject it. Wait, GameService doesn't have TransactionRepository.
        // I need to add TransactionRepository to GameService OR move this to WalletService.
        // Let's inject TransactionRepository into GameService, or just let Bet save natively and update tx?
        // Let's inject TransactionRepository. Wait! It's better to add the method to WalletService!
        // The user said: "Add to TransactionRepository instead" and "betRepository.updateTransactionRef". That was a typo by the user!
        // The user wrote: betRepository.updateTransactionRef(tx.getId(), bet.getId());
        // BUT they said to add the method to TransactionRepository!
        // Let me just inject TransactionRepository via constructor, wait, I can't easily add to constructor via replace.
        // I'll call a new method in WalletService: walletService.updateTransactionReference(tx.getId(), bet.getId());
        walletService.updateTransactionReference(tx.getId(), bet.getId());

        BetAckEvent ack = new BetAckEvent(
            bet.getId().toString(), bet.getBird(), bet.getSelectedRow(),
            bet.getAmountPaise(), tx.getBalanceAfterPaise(),
            request.getIdempotencyKey() != null ? request.getIdempotencyKey().toString() : null
        );
        gameBroadcastService.sendBetAck(user.getId(), ack);
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
