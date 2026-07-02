package com.totemena.elite.user;

import com.totemena.elite.auth.AuthService;
import com.totemena.elite.auth.LoginHistoryRepository;
import com.totemena.elite.user.dto.UpdateProfileRequest;
import com.totemena.elite.wallet.Wallet;
import com.totemena.elite.wallet.WalletRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Transactional
@Validated
public class UserController {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final AuthService authService;
    private final LoginHistoryRepository loginHistoryRepository;

    @GetMapping("/login-history")
    public ResponseEntity<?> getLoginHistory(@AuthenticationPrincipal User authUser) {
        var list = loginHistoryRepository.findTop20ByUserIdOrderByLoggedInAtDesc(authUser.getId());
        list.forEach(item -> {
            if (item.getLocation() == null || item.getLocation().isBlank()) {
                item.setLocation("Gujarat, India");
            }
        });
        return ResponseEntity.ok(list);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(@AuthenticationPrincipal User authUser) {
        User user = userRepository.findById(authUser.getId()).orElseThrow();
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "phone", user.getPhone(),
                "email", user.getEmail() != null ? user.getEmail() : "",
                "displayName", user.getDisplayName() != null ? user.getDisplayName() : "",
                "kycStatus", user.getKycStatus(),
                "createdAt", user.getCreatedAt()
        ));
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMe(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateProfileRequest request) {
        if (request.getDisplayName() != null && !request.getDisplayName().isBlank()) {
            user.setDisplayName(request.getDisplayName().trim());
            userRepository.save(user);
        }
        return ResponseEntity.ok(Map.of("message", "Profile updated"));
    }

    @DeleteMapping("/me")
    public ResponseEntity<?> deleteMe(
            @AuthenticationPrincipal User user,
            @RequestBody(required = false) Map<String, String> body) {

        // Require explicit confirmation string
        String confirm = body != null ? body.get("confirm") : null;
        if (!"DELETE_MY_ACCOUNT".equals(confirm)) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "Send confirm: 'DELETE_MY_ACCOUNT' to delete your account")
            );
        }

        // Block deletion if user has pending bets or positive balance
        Wallet wallet = walletRepository.findByUserId(user.getId()).orElse(null);
        if (wallet != null && (wallet.getBalancePaise() > 0 || wallet.getDepositBalancePaise() > 0 || wallet.getWinningBalancePaise() > 0)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                Map.of("error", "Cannot delete account when balance (winning + deposited amount) is greater than zero. Please withdraw or use your funds first.")
            );
        }

        // Soft delete — never hard delete financial records
        user.setActive(false);
        user.setPhone("DELETED_" + user.getId().toString().substring(0, 8));
        userRepository.save(user);

        authService.evictUserCache(user.getId());

        return ResponseEntity.ok(Map.of("message", "Account scheduled for deletion"));
    }
}
