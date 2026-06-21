package com.totemena.elite.game;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BetRepository extends JpaRepository<Bet, UUID> {

    Optional<Bet> findByIdempotencyKey(UUID idempotencyKey);

    Optional<Bet> findByRoundIdAndUserIdAndBird(UUID roundId, UUID userId, String bird);

    List<Bet> findByRoundId(UUID roundId);

    @Query("SELECT b FROM Bet b JOIN FETCH b.round WHERE b.user.id = :userId ORDER BY b.createdAt DESC")
    Page<Bet> findUserHistory(UUID userId, Pageable pageable);
    
    List<Bet> findByRoundIdAndUserId(UUID roundId, UUID userId);
    
    List<Bet> findByUserIdAndRoundIdIn(UUID userId, java.util.List<UUID> roundIds);

    @Query("SELECT COALESCE(SUM(b.amountPaise), 0) FROM Bet b WHERE b.createdAt >= :since")
    Long sumBetsSince(java.time.Instant since);

    @Query("SELECT COALESCE(MAX(b.payoutPaise), 0) FROM Bet b WHERE b.createdAt >= :since")
    Long maxPayoutSince(java.time.Instant since);

    @Query("SELECT COUNT(b) FROM Bet b WHERE b.user.id = :userId")
    Long countBetsByUser(UUID userId);

    @Query("SELECT COUNT(b) FROM Bet b WHERE b.user.id = :userId AND b.status = 'WON'")
    Long countWonBetsByUser(UUID userId);

    @Query("SELECT COALESCE(MAX(CAST(b.payoutPaise AS double) / b.amountPaise), 0.0) FROM Bet b WHERE b.user.id = :userId AND b.status = 'WON'")
    Double maxMultiplierByUser(UUID userId);

    @Query("SELECT COUNT(DISTINCT b.round.id) FROM Bet b WHERE b.user.id = :userId")
    Long countDistinctRoundByUserId(UUID userId);

    @Query("SELECT COALESCE(SUM(b.payoutPaise), 0) FROM Bet b WHERE b.user.id = :userId AND b.status = 'WON'")
    Long sumWinningsByUser(UUID userId);
}
