package com.totemena.elite.auth;

import com.totemena.elite.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtConfig jwtConfig;
    private final StringRedisTemplate redisTemplate;
    private final SecretKey key;

    public JwtService(JwtConfig jwtConfig, StringRedisTemplate redisTemplate) {
        this.jwtConfig = jwtConfig;
        this.redisTemplate = redisTemplate;
        if (jwtConfig.getSecret() == null || jwtConfig.getSecret().length() < 64) {
            throw new IllegalArgumentException("JWT secret must be at least 64 characters long");
        }
        this.key = Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UUID userId, String phone) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("phone", phone)
                .id(UUID.randomUUID().toString()) // jti
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusMillis(jwtConfig.getAccessExpiryMs())))
                .signWith(key)
                .compact();
    }

    public boolean isValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            // Check Redis revocation list
            String jti = claims.getId();
            if (Boolean.TRUE.equals(redisTemplate.hasKey("jwt:revoked:" + jti))) {
                return false;
            }
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isExpired(String token) {
        try {
            extractAllClaims(token);
            return false; // Valid
        } catch (ExpiredJwtException e) {
            return true; // Specifically expired
        } catch (Exception e) {
            return false; // Malformed/Invalid, but not just expired
        }
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(extractAllClaims(token).getSubject());
    }

    public String extractJti(String token) {
        return extractAllClaims(token).getId();
    }

    public Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    public long getAccessExpiryMs() {
        return jwtConfig.getAccessExpiryMs();
    }
}
