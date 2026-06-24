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

        int updated = walletRepository.deductBalance(userId, amountPaise);
        if (updated == 0) {
            throw new InsufficientBalanceException("Insufficient balance");
        }

        long newBalance = walletRepository.getBalanceAfterDeduct(userId);

        User user = userRepository.getReferenceById(userId);
        
        WalletTransaction tx = WalletTransaction.builder()
                .user(user)
                .type(type)
                .amountPaise(-amountPaise)
                .balanceAfterPaise(newBalance)
                .referenceId(referenceId)
                .build();
                
        return transactionRepository.save(tx);
    }

    @Transactional
    public WalletTransaction addFunds(UUID userId, long amountPaise, String type, UUID referenceId) {
        if (amountPaise <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        int updated = walletRepository.addBalance(userId, amountPaise);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found");
        }

        long newBalance = walletRepository.getBalanceAfterAdd(userId);

        User user = userRepository.getReferenceById(userId);

        WalletTransaction tx = WalletTransaction.builder()
                .user(user)
                .type(type)
                .amountPaise(amountPaise)
                .balanceAfterPaise(newBalance)
                .referenceId(referenceId)
                .build();

        return transactionRepository.save(tx);
    }
}
