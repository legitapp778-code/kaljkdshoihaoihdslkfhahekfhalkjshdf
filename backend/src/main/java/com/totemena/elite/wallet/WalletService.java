package com.totemena.elite.wallet;

import com.totemena.elite.common.exception.InsufficientBalanceException;
import com.totemena.elite.user.User;
import com.totemena.elite.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public Wallet getWallet(UUID userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found"));
    }

    public long getBalance(UUID userId) {
        Long balance = walletRepository.getBalanceAfterDeduct(userId);
        if (balance == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found");
        }
        return balance;
    }

    public Page<WalletTransaction> getTransactions(UUID userId, int page, int size, String type) {
        if (type != null && !type.isBlank()) {
            return transactionRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type, PageRequest.of(page, size));
        }
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }

    @Transactional
    public void updateTransactionReference(UUID txId, UUID referenceId) {
        transactionRepository.updateReferenceId(txId, referenceId);
    }

    @Transactional
    public WalletTransaction deductFunds(UUID userId, long amountPaise, String type, UUID referenceId) {
        if (amountPaise <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found"));

        if (wallet.getBalancePaise() < amountPaise) {
            throw new InsufficientBalanceException("Insufficient balance");
        }

        long depDeduct = Math.min(wallet.getDepositBalancePaise(), amountPaise);
        long winDeduct = amountPaise - depDeduct;

        wallet.setDepositBalancePaise(wallet.getDepositBalancePaise() - depDeduct);
        wallet.setWinningBalancePaise(wallet.getWinningBalancePaise() - winDeduct);
        wallet.setBalancePaise(wallet.getDepositBalancePaise() + wallet.getWinningBalancePaise());
        walletRepository.save(wallet);

        User user = userRepository.getReferenceById(userId);
        
        WalletTransaction tx = WalletTransaction.builder()
                .user(user)
                .type(type)
                .amountPaise(-amountPaise)
                .depositPaise(-depDeduct)
                .winningPaise(-winDeduct)
                .balanceAfterPaise(wallet.getBalancePaise())
                .referenceId(referenceId)
                .build();
                
        return transactionRepository.save(tx);
    }

    @Transactional
    public WalletTransaction addFunds(UUID userId, long amountPaise, String type, UUID referenceId) {
        if (amountPaise <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found"));

        long depAdd = 0;
        long winAdd = 0;

        if ("BET_WON".equals(type)) {
            winAdd = amountPaise;
        } else if ("BET_REFUND".equals(type)) {
            long netWinDeducted = 0;
            if (referenceId != null) {
                java.util.List<WalletTransaction> priorTxs = transactionRepository.findByReferenceId(referenceId);
                for (WalletTransaction prior : priorTxs) {
                    if (prior.getWinningPaise() < 0) {
                        netWinDeducted += Math.abs(prior.getWinningPaise());
                    } else if (prior.getWinningPaise() > 0) {
                        netWinDeducted -= prior.getWinningPaise();
                    }
                }
            }
            if (netWinDeducted < 0) netWinDeducted = 0;

            winAdd = Math.min(amountPaise, netWinDeducted);
            depAdd = amountPaise - winAdd;
        } else {
            // DEPOSIT or other additions go to deposit balance
            depAdd = amountPaise;
        }

        wallet.setDepositBalancePaise(wallet.getDepositBalancePaise() + depAdd);
        wallet.setWinningBalancePaise(wallet.getWinningBalancePaise() + winAdd);
        wallet.setBalancePaise(wallet.getDepositBalancePaise() + wallet.getWinningBalancePaise());
        walletRepository.save(wallet);

        User user = userRepository.getReferenceById(userId);

        WalletTransaction tx = WalletTransaction.builder()
                .user(user)
                .type(type)
                .amountPaise(amountPaise)
                .depositPaise(depAdd)
                .winningPaise(winAdd)
                .balanceAfterPaise(wallet.getBalancePaise())
                .referenceId(referenceId)
                .build();

        return transactionRepository.save(tx);
    }
}
