package com.totemena.elite.config;

import com.totemena.elite.auth.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
public class RateLimitConfig extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String ip = getClientIp(request);

        boolean allowed;

        if (path.equals("/api/v1/auth/send-otp")) {
            allowed = tryConsumeRedis("send_otp", ip, 15, 600); // 15 requests per 10 mins
        } else if (path.equals("/api/v1/auth/verify-otp")) {
            allowed = tryConsumeRedis("verify_otp", ip, 5, 600); // 5 requests per 10 mins
        } else if (path.equals("/api/v1/auth/refresh")) {
            allowed = tryConsumeRedis("refresh", ip, 10, 60); // 10 requests per 1 min
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
            allowed = tryConsumeRedis("rest_default", key, 120, 60); // 120 requests per 1 min
        }

        if (allowed) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded\"}");
        }
    }

    private boolean tryConsumeRedis(String prefix, String identifier, int maxRequests, long windowSeconds) {
        long currentWindow = System.currentTimeMillis() / (windowSeconds * 1000L);
        String redisKey = "rate_limit:" + prefix + ":" + identifier + ":" + currentWindow;
        try {
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redisTemplate.expire(redisKey, windowSeconds + 10, TimeUnit.SECONDS);
            }
            return count != null && count <= maxRequests;
        } catch (Exception e) {
            // Fallback to allow request if Redis connection fails temporarily
            return true;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }
}
