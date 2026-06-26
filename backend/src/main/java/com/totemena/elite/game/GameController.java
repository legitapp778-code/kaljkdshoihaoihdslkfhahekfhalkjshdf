package com.totemena.elite.game;

import com.totemena.elite.game.dto.BetResultResponse;
import com.totemena.elite.game.dto.PlaceBetRequest;
import com.totemena.elite.user.User;
import com.totemena.elite.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/game")
@RequiredArgsConstructor
@Validated
public class GameController {

    private final GameService gameService;
    private final BetRepository betRepository;
    private final RoundRepository roundRepository;
    private final StringRedisTemplate redisTemplate;
    private final WalletService walletService;

    @PostMapping("/bet")
    public ResponseEntity<BetResultResponse> placeBet(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody PlaceBetRequest request) {
        return ResponseEntity.ok(gameService.placeBet(user, request));
    }

    @DeleteMapping("/bet/{bird}")
    public ResponseEntity<Map<String, Object>> cancelBet(
            @AuthenticationPrincipal User user,
            @PathVariable @Pattern(regexp = "^(tota|mena)$",
                message = "Bird must be 'tota' or 'mena'") String bird) {
        long newBalance = gameService.cancelBet(user, bird);
        return ResponseEntity.ok(Map.of(
                "status", "CANCELLED",
                "bird", bird,
                "newBalancePaise", newBalance
        ));
    }

    @GetMapping("/current")
    public ResponseEntity<?> getCurrentState(@AuthenticationPrincipal User user) {
        String roundIdStr = redisTemplate.opsForValue().get("game:current_round_id");
        if (roundIdStr == null) {
            return ResponseEntity.ok(Map.of("status", "NO_ACTIVE_ROUND"));
        }

        String phase = redisTemplate.opsForValue().get("game:round:" + roundIdStr + ":phase");
        
        // Include user's active bets for this round so frontend can sync
        List<Bet> activeBets = betRepository.findByRoundIdAndUserId(UUID.fromString(roundIdStr), user.getId());
        List<Map<String, Object>> serializedBets = activeBets.stream().map(b -> {
            Map<String, Object> betMap = new java.util.HashMap<>();
            betMap.put("id", b.getId());
            betMap.put("bird", b.getBird());
            betMap.put("selectedRow", b.getSelectedRow());
            betMap.put("amountPaise", b.getAmountPaise());
            return betMap;
        }).collect(Collectors.toList());

        long balance = walletService.getWallet(user.getId()).getBalancePaise();

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("roundId", roundIdStr);
        response.put("phase", phase != null ? phase : "UNKNOWN");
        response.put("activeBets", serializedBets);
        response.put("balance", balance);
        response.put("multiplierTota", new java.math.BigDecimal("2.00"));
        response.put("multiplierMena", new java.math.BigDecimal("1.50"));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<Page<Map<String, Object>>> getHistory(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 50); // cap at 50 — never let user dump entire table
        page = Math.max(page, 0);  // no negative pages
        Page<Bet> bets = betRepository.findUserHistory(user.getId(), PageRequest.of(page, size));
        Page<Map<String, Object>> dtoPage = bets.map(b -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", b.getId());
            map.put("roundId", b.getRound().getId());
            map.put("bird", b.getBird());
            map.put("selectedRow", b.getSelectedRow());
            map.put("amountPaise", b.getAmountPaise());
            map.put("payoutPaise", b.getPayoutPaise());
            map.put("status", b.getStatus());
            map.put("createdAt", b.getCreatedAt());
            return map;
        });
        return ResponseEntity.ok(dtoPage);
    }

    @GetMapping("/round/{roundId}/result")
    public ResponseEntity<?> getRoundResult(@PathVariable UUID roundId, @AuthenticationPrincipal User user) {
        Optional<Round> roundOpt = roundRepository.findById(roundId);
        if (roundOpt.isEmpty() || !"FINISHED".equals(roundOpt.get().getStatus())) {
            return ResponseEntity.notFound().build();
        }

        Round round = roundOpt.get();
        List<Bet> myBets = betRepository.findByRoundIdAndUserId(roundId, user.getId());
        long currentBalance = walletService.getWallet(user.getId()).getBalancePaise();

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("roundId", round.getId());
        response.put("winningRowTota", round.getWinningRowTota());
        response.put("winningRowMena", round.getWinningRowMena());
        
        List<Map<String, Object>> myResults = myBets.stream().map(b -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("bird", b.getBird());
            map.put("selectedRow", b.getSelectedRow());
            map.put("status", b.getStatus());
            map.put("payoutPaise", b.getPayoutPaise() != null ? b.getPayoutPaise() : 0);
            return map;
        }).collect(Collectors.toList());
        
        response.put("myResults", myResults);
        response.put("currentBalancePaise", currentBalance);

        return ResponseEntity.ok(response);
    }
}
