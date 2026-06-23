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
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public BetResultResponse placeBet(User user, PlaceBetRequest request) {
        StopWatch sw = new StopWatch();
        sw.start("idempotencyCheck");
        // Idempotency check
        Optional<Bet> existingBetByIdempotency = betRepository.findByIdempotencyKey(request.getIdempotencyKey());
        sw.stop();
        
        if (existingBetByIdempotency.isPresent()) {
            Bet bet = existingBetByIdempotency.get();
            return BetResultResponse.builder()
                    .betId(bet.getId())
                    .bird(bet.getBird())
                    .selectedRow(bet.getSelectedRow())
                    .amountPaise(bet.getAmountPaise())
                    .balanceAfterPaise(walletService.getWallet(user.getId()).getBalancePaise())
                    .build();
        }

        sw.start("redisStateCheck");
        String currentRoundIdStr = redisTemplate.opsForValue().get("game:current_round_id");
        if (currentRoundIdStr == null) {
            throw new BettingClosedException("Game is not active");
        }

        String phase = redisTemplate.opsForValue().get("game:round:" + currentRoundIdStr + ":phase");
        sw.stop();
        
        if (!"BETTING".equals(phase)) {
            throw new BettingClosedException("Betting is closed for this round");
        }

        sw.start("fetchRound");
        UUID roundId = UUID.fromString(currentRoundIdStr);
        Round round = roundRepository.getReferenceById(roundId);
        sw.stop();

        sw.start("existingBetCheck");
        Optional<Bet> existingBetOnBird = betRepository.findByRoundIdAndUserIdAndBird(roundId, user.getId(), request.getBird());
        sw.stop();
        
        long amountToDeduct = request.getAmountPaise();
        long finalBalance;
        Bet savedBet;

        sw.start("walletUpdateAndBetSave");
        if (existingBetOnBird.isPresent()) {
            Bet existingBet = existingBetOnBird.get();
            long diff = request.getAmountPaise() - existingBet.getAmountPaise();
            
            if (diff > 0) {
                // Deduct more
                WalletTransaction tx = walletService.deductFunds(user.getId(), diff, "BET_UPDATED", existingBet.getId());
                existingBet.setAmountPaise(request.getAmountPaise());
                existingBet.setSelectedRow(request.getSelectedRow());
                savedBet = betRepository.save(existingBet);
                finalBalance = tx.getBalanceAfterPaise();
            } else if (diff < 0) {
                // Refund difference
                long refund = Math.abs(diff);
                WalletTransaction tx = walletService.addFunds(user.getId(), refund, "BET_REFUND", existingBet.getId());
                existingBet.setAmountPaise(request.getAmountPaise());
                existingBet.setSelectedRow(request.getSelectedRow());
                savedBet = betRepository.save(existingBet);
                finalBalance = tx.getBalanceAfterPaise();
            } else {
                // Same amount, just change row
                existingBet.setSelectedRow(request.getSelectedRow());
                savedBet = betRepository.save(existingBet);
                finalBalance = walletService.getWallet(user.getId()).getBalancePaise();
            }
        } else {
            // New bet
            Bet newBet = Bet.builder()
                    .round(round)
                    .user(user)
                    .bird(request.getBird())
                    .selectedRow(request.getSelectedRow())
                    .amountPaise(request.getAmountPaise())
                    .idempotencyKey(request.getIdempotencyKey())
                    .status("PENDING")
                    .build();
                    
            savedBet = betRepository.save(newBet);
            WalletTransaction tx = walletService.deductFunds(user.getId(), request.getAmountPaise(), "BET_PLACED", savedBet.getId());
            finalBalance = tx.getBalanceAfterPaise();
        }
        sw.stop();

        sw.start("sendAck");
        BetAckEvent ackEvent = new BetAckEvent(
                savedBet.getId().toString(),
                savedBet.getBird(),
                savedBet.getSelectedRow(),
                savedBet.getAmountPaise(),
                finalBalance,
                savedBet.getIdempotencyKey().toString()
        );

        gameBroadcastService.sendBetAck(user.getId(), ackEvent);
        sw.stop();
        
        log.info("PlaceBet profiling: {}", sw.prettyPrint());

        return BetResultResponse.builder()
                .betId(savedBet.getId())
                .bird(savedBet.getBird())
                .selectedRow(savedBet.getSelectedRow())
                .amountPaise(savedBet.getAmountPaise())
                .balanceAfterPaise(finalBalance)
                .build();
    }

    @Transactional
    public long cancelBet(User user, String bird) {
        // Only allow cancel during BETTING phase
        String currentRoundIdStr = redisTemplate.opsForValue().get("game:current_round_id");
        if (currentRoundIdStr == null) {
            throw new BettingClosedException("Game is not active");
        }
        String phase = redisTemplate.opsForValue().get("game:round:" + currentRoundIdStr + ":phase");
        if (!"BETTING".equals(phase)) {
            throw new BettingClosedException("Betting is closed — cannot cancel now");
        }

        UUID roundId = UUID.fromString(currentRoundIdStr);
        Optional<Bet> betOpt = betRepository.findByRoundIdAndUserIdAndBird(roundId, user.getId(), bird);
        if (betOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No active bet found for " + bird);
        }

        Bet bet = betOpt.get();
        if (!"PENDING".equals(bet.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bet is already resolved");
        }

        long refundAmount = bet.getAmountPaise();
        walletService.addFunds(user.getId(), refundAmount, "BET_CANCELLED", bet.getId());
        betRepository.delete(bet);

        long finalBalance = walletService.getWallet(user.getId()).getBalancePaise();

        // Notify user of refund via WS
        gameBroadcastService.sendBalanceUpdate(
                user.getId(), finalBalance, refundAmount,
                currentRoundIdStr, "BET_CANCELLED", null);

        return finalBalance;
    }
}
