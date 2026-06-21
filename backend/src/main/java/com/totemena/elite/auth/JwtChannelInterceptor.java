package com.totemena.elite.auth;

import com.totemena.elite.user.User;
import com.totemena.elite.user.UserRepository;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
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

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    
    // In-memory rate limiting for WS frames (30 frames / min)
    private final Map<UUID, Bucket> wsRateLimiters = new ConcurrentHashMap<>();

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
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new MessagingException("USER_NOT_FOUND"));
            
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

    private String extractBearerToken(String header) {
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private void enforceWsRateLimit(UUID userId) {
        Bucket bucket = wsRateLimiters.computeIfAbsent(userId, k -> 
            Bucket.builder()
                  .addLimit(Bandwidth.classic(30, Refill.greedy(30, Duration.ofMinutes(1))))
                  .build()
        );
        
        if (!bucket.tryConsume(1)) {
            throw new MessagingException("RATE_LIMIT_EXCEEDED");
        }
    }
}
