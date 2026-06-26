package com.totemena.elite.auth;

import com.totemena.elite.auth.dto.TokenResponse;
import com.totemena.elite.config.JwtConfig;
import com.totemena.elite.user.User;
import com.totemena.elite.user.UserRepository;
import com.totemena.elite.wallet.Wallet;
import com.totemena.elite.wallet.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final OtpService otpService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final StringRedisTemplate redisTemplate;
    private final JwtConfig jwtConfig;

    public void sendOtp(String phone) {
        otpService.generateAndSendOtp(phone);
    }

    public void evictUserCache(UUID userId) {
        if (userId != null) {
            redisTemplate.delete("user:cache:" + userId);
        }
    }

    @Transactional
    public TokenResponse verifyOtp(com.totemena.elite.auth.dto.VerifyOtpRequest request) {
        String phone = request.getPhone();
        String otp = request.getOtp();

        if (!otpService.verifyOtp(phone, otp)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired OTP");
        }

        // Upsert User
        User user = userRepository.findByPhone(phone).orElseGet(() -> {
            User newUser = User.builder().phone(phone).build();
            userRepository.save(newUser);
            
            // Create initial wallet
            walletRepository.save(Wallet.builder().user(newUser).balancePaise(0L).build());
            return newUser;
        });

        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is disabled");
        }

        // Update name and email if provided
        boolean updated = false;
        if (request.getDisplayName() != null && !request.getDisplayName().isBlank()) {
            String name = request.getDisplayName().trim();
            if (name.length() > 50) name = name.substring(0, 50); // hard cap
            user.setDisplayName(name);
            updated = true;
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String email = request.getEmail().trim();
            if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email format");
            }
            if (email.length() > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email too long");
            }
            user.setEmail(email);
            updated = true;
        }
        if (updated) {
            userRepository.save(user);
        }

        return generateTokens(user);
    }

    @Transactional
    public TokenResponse refresh(String plainRefreshToken) {
        return refreshInternal(plainRefreshToken);
    }

    @Transactional
    public TokenResponse refreshForReconnect(String plainRefreshToken) {
        // Same logic, but explicitly named for the reconnect flow
        return refreshInternal(plainRefreshToken);
    }

    private TokenResponse refreshInternal(String plainRefreshToken) {
        String hash = hashToken(plainRefreshToken);
        
        RefreshToken refreshTokenEntity = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (refreshTokenEntity.isRevoked() || refreshTokenEntity.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired or revoked");
        }

        User user = refreshTokenEntity.getUser();
        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is disabled");
        }

        // Revoke old token
        refreshTokenEntity.setRevoked(true);
        refreshTokenRepository.save(refreshTokenEntity);

        return generateTokens(user);
    }

    public void logout(String accessToken) {
        try {
            if (accessToken != null && accessToken.startsWith("Bearer ")) {
                accessToken = accessToken.substring(7);
            }
            if (jwtService.isValid(accessToken)) {
                String jti = jwtService.extractJti(accessToken);
                UUID userId = jwtService.extractUserId(accessToken);
                long remainingMs = jwtService.extractExpiration(accessToken).getTime() - System.currentTimeMillis();
                if (remainingMs > 0) {
                    redisTemplate.opsForValue().set("jwt:revoked:" + jti, "true", remainingMs, TimeUnit.MILLISECONDS);
                }
                if (userId != null) {
                    redisTemplate.delete("user:cache:" + userId);
                }
            }
        } catch (Exception ignored) {
            // Best effort
        }
    }

    private TokenResponse generateTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getPhone());
        String plainRefreshToken = UUID.randomUUID().toString() + UUID.randomUUID().toString();
        String hash = hashToken(plainRefreshToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hash)
                .expiresAt(Instant.now().plusMillis(jwtConfig.getRefreshExpiryMs()))
                .build();
        
        refreshTokenRepository.save(refreshToken);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(plainRefreshToken)
                .expiresIn(jwtConfig.getAccessExpiryMs() / 1000)
                .build();
    }

    private String hashToken(String plainToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plainToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
