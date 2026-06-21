package com.totemena.elite.wallet;

import com.totemena.elite.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    public ResponseEntity<?> getWallet(@AuthenticationPrincipal User user) {
        Wallet wallet = walletService.getWallet(user.getId());
        
        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        String displayBalance = format.format(wallet.getBalancePaise() / 100.0);
        
        return ResponseEntity.ok(Map.of(
            "balancePaise", wallet.getBalancePaise(),
            "displayBalance", displayBalance
        ));
    }

    @GetMapping("/transactions")
    public ResponseEntity<Page<WalletTransaction>> getTransactions(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type
    ) {
        return ResponseEntity.ok(walletService.getTransactions(user.getId(), page, size, type));
    }
}
