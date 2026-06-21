package com.totemena.elite.game;

import com.totemena.elite.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/global")
    public ResponseEntity<Map<String, Object>> getGlobalStats() {
        return ResponseEntity.ok(statsService.getGlobalStats());
    }

    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> getUserStats(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(statsService.getUserStats(user.getId()));
    }

    @GetMapping("/history")
    public ResponseEntity<java.util.List<Map<String, Object>>> getGlobalHistory(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(statsService.getGlobalHistory(user));
    }
}
