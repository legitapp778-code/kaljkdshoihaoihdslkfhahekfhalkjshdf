package com.totemena.elite.game;

import com.totemena.elite.wallet.WalletService;
import com.totemena.elite.wallet.WalletTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GamePayoutService {

    private final BetRepository betRepository;
    private final WalletService walletService;
    private final GameBroadcastService gameBroadcastService;

    @Async("payoutExecutor")
    @Transactional
    public void processPayoutsAsync(UUID roundId, short winTota, short winMena, String roundIdStr) {
        List<Bet> bets = betRepository.findByRoundId(roundId);
        Map<UUID, Long> balanceCache = new HashMap<>();

        for (Bet bet : bets) {
            try {
                boolean won = false;
                double betMultiplier = 0.0;

                if ("tota".equals(bet.getBird()) && bet.getSelectedRow() == winTota) {
                    won = true;
                    betMultiplier = 2.0;
                } else if ("mena".equals(bet.getBird()) && bet.getSelectedRow() == winMena) {
                    won = true;
                    betMultiplier = 1.5;
                }

                if (won) {
                    long payout = BigDecimal.valueOf(bet.getAmountPaise())
                        .multiply(BigDecimal.valueOf(betMultiplier))
                        .setScale(0, RoundingMode.DOWN)
                        .longValue();

                    bet.setStatus("WON");
                    bet.setPayoutPaise(payout);
                    betRepository.save(bet);

                    // addFunds returns the transaction which has balanceAfterPaise
                    WalletTransaction tx = walletService.addFunds(
                        bet.getUser().getId(), payout, "BET_WON", bet.getId());

                    BigDecimal mult = "tota".equals(bet.getBird()) ?
                        new BigDecimal("2.00") : new BigDecimal("1.50");

                    gameBroadcastService.sendBalanceUpdate(
                        bet.getUser().getId(),
                        tx.getBalanceAfterPaise(),   // use value from tx — no second DB call
                        payout, roundIdStr, "BET_WON", mult);
                } else {
                    bet.setStatus("LOST");
                    bet.setPayoutPaise(0L);
                    betRepository.save(bet);

                    // For LOST: get balance from wallet service only once
                    long balance = balanceCache.computeIfAbsent(
                        bet.getUser().getId(),
                        uid -> walletService.getWallet(uid).getBalancePaise()
                    );
                    
                    BigDecimal mult = "tota".equals(bet.getBird()) ?
                        new BigDecimal("2.00") : new BigDecimal("1.50");

                    gameBroadcastService.sendBalanceUpdate(
                        bet.getUser().getId(),
                        balance, 0L, roundIdStr, "BET_LOST", mult);
                }
            } catch (Exception e) {
                log.error("Error processing payout for bet {}: {}", bet.getId(), e.getMessage(), e);
            }
        }

        log.info("Payouts processed for round {}: {} bets", roundIdStr, bets.size());
    }
}
