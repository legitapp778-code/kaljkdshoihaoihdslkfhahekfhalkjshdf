package com.totemena.elite.auth;

import com.totemena.elite.auth.dto.TokenResponse;
import com.totemena.elite.config.JwtConfig;
import com.totemena.elite.user.User;
import com.totemena.elite.user.UserRepository;
import com.totemena.elite.wallet.Wallet;
import com.totemena.elite.wallet.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final OtpService otpService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginHistoryRepository loginHistoryRepository;
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
        return verifyOtp(request, null, null);
    }

    @Transactional
    public TokenResponse verifyOtp(com.totemena.elite.auth.dto.VerifyOtpRequest request, String ipAddress, String userAgent) {
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

        try {
            String stateLocation = resolveStateFromIp(ipAddress);
            LoginHistory history = LoginHistory.builder()
                    .user(user)
                    .deviceName(parseDeviceName(userAgent))
                    .ipAddress(ipAddress != null && ipAddress.length() > 64 ? ipAddress.substring(0, 64) : ipAddress)
                    .location(stateLocation)
                    .userAgent(userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent)
                    .loggedInAt(Instant.now())
                    .build();
            loginHistoryRepository.save(history);
        } catch (Exception e) {
            log.warn("Failed to record login history: {}", e.getMessage());
        }

        return generateTokens(user);
    }

    private String resolveStateFromIp(String ip) {
        if (ip == null || ip.isBlank() || "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || ip.startsWith("192.168.") || ip.startsWith("10.")) {
            return "Gujarat, India";
        }
        try {
            java.net.URL url = new java.net.URL("http://ip-api.com/json/" + ip + "?fields=status,regionName");
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
            con.setConnectTimeout(1500);
            con.setReadTimeout(1500);
            if (con.getResponseCode() == 200) {
                try (java.io.InputStream in = con.getInputStream()) {
                    String json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    if (json.contains("\"status\":\"success\"") && json.contains("\"regionName\":\"")) {
                        int idx = json.indexOf("\"regionName\":\"") + 14;
                        int endIdx = json.indexOf("\"", idx);
                        if (endIdx > idx) {
                            String region = json.substring(idx, endIdx);
                            return region + ", India";
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "Gujarat, India";
    }

    private String parseDeviceName(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown Device";
        }
        if (userAgent.contains("iPhone")) {
            return "Apple iPhone";
        } else if (userAgent.contains("iPad")) {
            return "Apple iPad";
        } else if (userAgent.contains("Android")) {
            try {
                int androidIdx = userAgent.indexOf("Android");
                int semiIdx = userAgent.indexOf(";", androidIdx);
                if (semiIdx != -1) {
                    int nextSemiOrParen = userAgent.indexOf("Build/", semiIdx);
                    if (nextSemiOrParen == -1) nextSemiOrParen = userAgent.indexOf(")", semiIdx);
                    if (nextSemiOrParen != -1 && nextSemiOrParen > semiIdx + 1) {
                        String model = userAgent.substring(semiIdx + 1, nextSemiOrParen).trim();
                        if (model.endsWith(";")) model = model.substring(0, model.length() - 1).trim();
                        if (!model.isEmpty() && !model.startsWith("wv") && model.length() < 50) {
                            return model;
                        }
                    }
                }
            } catch (Exception ignored) {}
            return "Android Device";
        } else if (userAgent.contains("Windows NT")) {
            return "Windows PC";
        } else if (userAgent.contains("Macintosh") || userAgent.contains("Mac OS X")) {
            return "Apple Mac";
        } else if (userAgent.contains("Linux")) {
            return "Linux PC";
        }
        return "Web Browser";
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
