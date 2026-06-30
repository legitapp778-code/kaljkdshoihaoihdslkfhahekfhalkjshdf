package com.totemena.elite.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.concurrent.TimeUnit;
import com.totemena.elite.user.User;
import com.totemena.elite.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractBearerToken(accessor.getFirstNativeHeader("Authorization"));
            
            if (token == null) {
                throw new MessagingException("Missing Authorization header");
            }
            
            if (jwtService.isExpired(token)) {
                // Per user request: explicitly return TOKEN_EXPIRED so frontend knows to refresh
                throw new MessagingException("TOKEN_EXPIRED");
            }
            
            if (!jwtService.isValid(token)) {
                throw new MessagingException("INVALID_TOKEN");
            }

            UUID userId = jwtService.extractUserId(token);
            User user = getUserFromCacheOrDb(userId);
            if (user == null) {
                throw new MessagingException("USER_NOT_FOUND");
            }
            
            if (!user.isActive()) {
                throw new MessagingException("ACCOUNT_DISABLED");
            }

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()) {
                @Override
                public String getName() {
                    return user.getId().toString();
                }
            };
            accessor.setUser(auth);
        }

        if (StompCommand.SEND.equals(accessor.getCommand()) && accessor.getUser() != null) {
            User user = (User) ((UsernamePasswordAuthenticationToken) accessor.getUser()).getPrincipal();
            enforceWsRateLimit(user.getId());
        }

        return message;
    }

    private User getUserFromCacheOrDb(UUID userId) {
        String cacheKey = "user:cache:" + userId;
        try {
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null) {
                return objectMapper.readValue(cachedJson, User.class);
            }
        } catch (Exception ignored) {
            // Fall through to DB on cache error
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            try {
                redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(user),
                    1, TimeUnit.HOURS
                );
            } catch (Exception ignored) {}
        }
        return user;
    }

    private String extractBearerToken(String header) {
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private void enforceWsRateLimit(UUID userId) {
        long currentWindow = System.currentTimeMillis() / 60000L;
        String redisKey = "rate_limit:ws:" + userId + ":" + currentWindow;
        try {
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redisTemplate.expire(redisKey, 70, TimeUnit.SECONDS);
            }
            if (count != null && count > 30) {
                throw new MessagingException("RATE_LIMIT_EXCEEDED");
            }
        } catch (MessagingException me) {
            throw me;
        } catch (Exception ignored) {
            // Allow request if Redis connection fails temporarily
        }
    }
}
