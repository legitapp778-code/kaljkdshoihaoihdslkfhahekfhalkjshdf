package com.totemena.elite.game;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rounds")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Round {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "BETTING"; // BETTING, SPINNING, FINISHED

    @Column(name = "winning_row_tota")
    private Short winningRowTota;

    @Column(name = "winning_row_mena")
    private Short winningRowMena;

    @Column(name = "betting_starts_at", nullable = false)
    @Builder.Default
    private Instant bettingStartsAt = Instant.now();

    @Column(name = "spinning_at")
    private Instant spinningAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
