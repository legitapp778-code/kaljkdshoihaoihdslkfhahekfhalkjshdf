package com.totemena.elite.auth;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class OtpService {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.otp.value:1234}")
    private String fixedOtpValue;

    @Value("${app.otp.ttl-minutes:5}")
    private long otpTtlMinutes;

    @Value("${app.otp.max-attempts:3}")
    private int maxAttempts;

    @PostConstruct
    public void validateConfig() {
        if (fixedOtpValue == null || fixedOtpValue.length() < 4) {
            throw new IllegalStateException("app.otp.value must be at least 4 digits");
        }
    }

    public void generateAndSendOtp(String phone) {
        String attemptsKey = "otp:attempts:" + phone;
        String pendingKey = "otp:pending:" + phone;

        Long count = redisTemplate.opsForValue().increment(attemptsKey);
        if (count == null) count = 1L;
        
        if (count == 1) {
            redisTemplate.expire(attemptsKey, 10, TimeUnit.MINUTES);
        }
        
        if (count > maxAttempts) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many OTP attempts. Please try again later.");
        }

        // Store OTP
        redisTemplate.opsForValue().set(pendingKey, fixedOtpValue, otpTtlMinutes, TimeUnit.MINUTES);
        
        // TODO: Integrate actual SMS provider here
    }

    public boolean verifyOtp(String phone, String otp) {
        String pendingKey = "otp:pending:" + phone;
        String storedOtp = redisTemplate.opsForValue().get(pendingKey);

        if (storedOtp == null || !storedOtp.equals(otp)) {
            return false;
        }

        // One-shot OTP: delete immediately after successful use
        redisTemplate.delete(pendingKey);
        return true;
    }
}
