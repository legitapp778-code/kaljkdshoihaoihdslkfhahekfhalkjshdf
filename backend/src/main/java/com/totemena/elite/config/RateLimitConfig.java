package com.totemena.elite.config;

import com.totemena.elite.auth.JwtService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

//@Configuration
@RequiredArgsConstructor
public class RateLimitConfig extends OncePerRequestFilter {

    private final Map<String, Bucket> otpBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> refreshBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> defaultBuckets = new ConcurrentHashMap<>();
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String ip = request.getRemoteAddr();

        Bucket bucket;

        if (path.equals("/api/v1/auth/send-otp") || path.equals("/api/v1/auth/verify-otp")) {
            bucket = otpBuckets.computeIfAbsent(ip, k -> Bucket.builder()
                    .addLimit(Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(10))))
                    .build());
        } else if (path.equals("/api/v1/auth/refresh")) {
            bucket = refreshBuckets.computeIfAbsent(ip, k -> Bucket.builder()
                    .addLimit(Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1))))
                    .build());
        } else {
            // General REST endpoints: rate-limit by userId if authenticated, else fallback to IP
            String key = ip;
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    if (jwtService.isValid(token)) {
                        key = jwtService.extractUserId(token).toString();
                    }
                } catch (Exception ignored) {
                }
            }
            
            bucket = defaultBuckets.computeIfAbsent(key, k -> Bucket.builder()
                    .addLimit(Bandwidth.classic(120, Refill.greedy(120, Duration.ofMinutes(1))))
                    .build());
        }

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("{\"error\":\"Rate limit exceeded\"}");
        }
    }
}
