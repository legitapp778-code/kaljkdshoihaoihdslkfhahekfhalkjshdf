package com.totemena.elite.game;

import com.totemena.elite.wallet.TransactionRepository;
import com.totemena.elite.user.User;
import com.totemena.elite.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final BetRepository betRepository;
    private final RoundRepository roundRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final GameBroadcastService gameBroadcastService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Map<String, Object> getGlobalStats() {
        String cacheKey = "stats:global";
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, Map.class);
            }
        } catch (Exception e) {
            // ignore cache read errors
        }

        Map<String, Object> response = new HashMap<>();
        Instant startOfDay = ZonedDateTime.now(ZoneId.of("UTC")).toLocalDate().atStartOfDay(ZoneId.of("UTC")).toInstant();

        response.put("playersOnline", gameBroadcastService.getPlayersOnline());

        Long totalBetsPaise = betRepository.sumBetsSince(startOfDay);
        response.put("totalBetsTodayPaise", totalBetsPaise != null ? totalBetsPaise : 0);

        Long biggestWinPaise = betRepository.maxPayoutSince(startOfDay);
        response.put("biggestWinTodayPaise", biggestWinPaise != null ? biggestWinPaise : 0);

        String currentRoundId = redisTemplate.opsForValue().get("game:current_round_id");
        response.put("currentRoundId", currentRoundId);

        List<Round> recentRounds = roundRepository.findRecentFinishedRounds(PageRequest.of(0, 10));
        List<Map<String, Object>> recentResults = recentRounds.stream().map(r -> {
            Map<String, Object> map = new HashMap<>();
            map.put("roundId", r.getId().toString());
            map.put("winningRowTota", r.getWinningRowTota());
            map.put("winningRowMena", r.getWinningRowMena());
            return map;
        }).collect(Collectors.toList());
        
        response.put("recentResults", recentResults);

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), 15, TimeUnit.SECONDS);
        } catch (Exception e) {
            // ignore cache write errors
        }

        return response;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, Object> getUserStats(UUID userId) {
        String userCacheKey = "stats:user:" + userId;
        try {
            String cachedUserStats = redisTemplate.opsForValue().get(userCacheKey);
            if (cachedUserStats != null) {
                return objectMapper.readValue(cachedUserStats, Map.class);
            }
        } catch (Exception e) {
            // ignore
        }

        User user = userRepository.getReferenceById(userId);
        Map<String, Object> response = new HashMap<>();

        Long totalGames = betRepository.countDistinctRoundByUserId(userId);
        Long wonGames = betRepository.countWonBetsByUser(userId);
        
        double winRate = (totalGames != null && totalGames > 0) ? ((double) wonGames / totalGames) * 100.0 : 0.0;
        response.put("phone", user.getPhone());
        response.put("gamesPlayed", totalGames != null ? totalGames : 0);
        response.put("winRate", winRate);
        response.put("bestMultiplier", 2.0); // Fixed multiplier for now

        Long totalWinnings = betRepository.sumWinningsByUser(userId);
        response.put("totalWinningsPaise", totalWinnings != null ? totalWinnings : 0);

        Long deposited = transactionRepository.sumAmountByUserIdAndTypeThisMonth(userId, "DEPOSIT");
        response.put("depositedThisMonthPaise", deposited != null ? deposited : 0);

        Long withdrawn = transactionRepository.sumAmountByUserIdAndTypeThisMonth(userId, "WITHDRAWAL");
        response.put("withdrawnThisMonthPaise", withdrawn != null ? Math.abs(withdrawn) : 0);

        response.put("vipTier", "STANDARD");

        try {
            redisTemplate.opsForValue().set(userCacheKey, objectMapper.writeValueAsString(response), 5, TimeUnit.SECONDS);
        } catch (Exception e) {
            // ignore
        }

        return response;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Map<String, Object>> getGlobalHistory(User user) {
        List<Round> historyRounds = roundRepository.findRecentFinishedRounds(PageRequest.of(0, 30));
        if (historyRounds.isEmpty()) return java.util.Collections.emptyList();

        List<UUID> roundIds = historyRounds.stream().map(Round::getId).collect(Collectors.toList());

        // One query to get all user bets for these rounds — not a loop
        Map<String, Bet> userBetsByKey = java.util.Collections.emptyMap();
        if (user != null) {
            List<Bet> userBets = betRepository.findByUserIdAndRoundIdIn(user.getId(), roundIds);
            userBetsByKey = userBets.stream()
                .collect(Collectors.toMap(
                    b -> b.getRound().getId() + ":" + b.getBird(),
                    b -> b,
                    (a, b) -> a   // keep first on duplicate
                ));
        }

        final Map<String, Bet> betMap = userBetsByKey;

        return historyRounds.stream().map(r -> {
            Map<String, Object> map = new HashMap<>();
            map.put("roundId", r.getId().toString());
            map.put("winningRowTota", r.getWinningRowTota());
            map.put("winningRowMena", r.getWinningRowMena());
            map.put("finishedAt", r.getFinishedAt() != null ? r.getFinishedAt().toString() : null);

            Bet totaBet = betMap.get(r.getId() + ":tota");
            Bet menaBet = betMap.get(r.getId() + ":mena");

            map.put("totaBetPaise",  totaBet != null ? totaBet.getAmountPaise()  : null);
            map.put("totaWinPaise",  totaBet != null ? totaBet.getPayoutPaise()  : null);
            map.put("menaBetPaise",  menaBet != null ? menaBet.getAmountPaise()  : null);
            map.put("menaWinPaise",  menaBet != null ? menaBet.getPayoutPaise()  : null);

            return map;
        }).collect(Collectors.toList());
    }
}
