package com.totemena.elite.auth;

import com.totemena.elite.auth.dto.RefreshTokenRequest;
import com.totemena.elite.auth.dto.SendOtpRequest;
import com.totemena.elite.auth.dto.TokenResponse;
import com.totemena.elite.auth.dto.VerifyOtpRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import com.totemena.elite.user.User;
import com.totemena.elite.user.UserRepository;
import com.totemena.elite.wallet.Wallet;
import com.totemena.elite.wallet.WalletRepository;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        if (Boolean.TRUE.equals(request.getIsSignIn())) {
            if (userRepository.findByPhone(request.getPhone()).isEmpty()) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Account not registered. Please Sign Up first!"));
            }
        } else if (Boolean.FALSE.equals(request.getIsSignIn())) {
            if (userRepository.findByPhone(request.getPhone()).isPresent()) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Phone number already registered. Please Sign In!"));
            }
        }
        authService.sendOtp(request.getPhone());
        return ResponseEntity.ok(Map.of("message", "OTP sent"));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        if (Boolean.TRUE.equals(request.getIsSignIn())) {
            if (userRepository.findByPhone(request.getPhone()).isEmpty()) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Account not registered. Please Sign Up first!"));
            }
        } else if (Boolean.FALSE.equals(request.getIsSignIn())) {
            if (request.getDisplayName() == null || request.getDisplayName().isBlank() ||
                request.getEmail() == null || request.getEmail().isBlank()) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "All fields (Full Name, Email, Mobile) are required for Sign Up!"));
            }
        }
        return ResponseEntity.ok(authService.verifyOtp(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        authService.logout(authHeader);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/dev/demo")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> setupDemo() {
        String[] phones = {"9999999991", "9999999992"};
        for (String p : phones) {
            User u = userRepository.findByPhone(p).orElse(null);
            if (u == null) {
                u = User.builder().phone(p).displayName("Demo User " + p).build();
                userRepository.save(u);
            }
            Wallet w = walletRepository.findByUserId(u.getId()).orElse(null);
            if (w == null) {
                w = Wallet.builder().user(u).balancePaise(100000000L).build(); // ₹1,000,000.00
                walletRepository.save(w);
            } else {
                w.setBalancePaise(100000000L);
                walletRepository.save(w);
            }
        }
        return ResponseEntity.ok(Map.of("message", "Demo accounts 9999999991 and 9999999992 created with ₹1,000,000 balance each. Use OTP '1234' to login."));
    }
}
