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

    public Page<WalletTransaction> getTransactions(UUID userId, int page, int size, String type) {
        if (type != null && !type.isBlank()) {
            return transactionRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type, PageRequest.of(page, size));
        }
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }

    @Transactional
    public WalletTransaction deductFunds(UUID userId, long amountPaise, String type, UUID referenceId) {
        if (amountPaise <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        int updated = walletRepository.deductBalance(userId, amountPaise);
        if (updated == 0) {
            throw new InsufficientBalanceException("Insufficient balance");
        }

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found"));

        User user = userRepository.getReferenceById(userId);
        
        WalletTransaction tx = WalletTransaction.builder()
                .user(user)
                .type(type)
                .amountPaise(-amountPaise) // negative for deduction
                .balanceAfterPaise(wallet.getBalancePaise())
                .referenceId(referenceId)
                .build();
                
        return transactionRepository.save(tx);
    }

    @Transactional
    public WalletTransaction addFunds(UUID userId, long amountPaise, String type, UUID referenceId) {
        if (amountPaise <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        int updated = walletRepository.addBalance(userId, amountPaise);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found");
        }

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found"));

        User user = userRepository.getReferenceById(userId);

        WalletTransaction tx = WalletTransaction.builder()
                .user(user)
                .type(type)
                .amountPaise(amountPaise) // positive for addition
                .balanceAfterPaise(wallet.getBalancePaise())
                .referenceId(referenceId)
                .build();

        return transactionRepository.save(tx);
    }
}
