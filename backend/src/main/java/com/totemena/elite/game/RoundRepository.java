package com.totemena.elite.game;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface RoundRepository extends JpaRepository<Round, UUID> {
    Optional<Round> findFirstByStatusOrderByCreatedAtDesc(String status);
    
    @org.springframework.data.jpa.repository.Query("SELECT r FROM Round r WHERE r.status = 'FINISHED' ORDER BY r.finishedAt DESC")
    java.util.List<Round> findRecentFinishedRounds(org.springframework.data.domain.Pageable pageable);
}
