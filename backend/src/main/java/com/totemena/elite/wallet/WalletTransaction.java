package com.totemena.elite.wallet;

import com.totemena.elite.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 20)
    private String type; // BET_PLACED, BET_WON, BET_LOST, DEPOSIT, WITHDRAWAL

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(name = "deposit_paise", nullable = false)
    @Builder.Default
    private long depositPaise = 0L;

    @Column(name = "winning_paise", nullable = false)
    @Builder.Default
    private long winningPaise = 0L;

    @Column(name = "balance_after_paise", nullable = false)
    private long balanceAfterPaise;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
