package com.totemena.elite.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByUserId(UUID userId);

    /** Pessimistic lock for safe balance mutations */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.user.id = :userId")
    Optional<Wallet> findByUserIdForUpdate(UUID userId);

    @Modifying
    @Query("UPDATE Wallet w SET w.balancePaise = w.balancePaise - :amount WHERE w.user.id = :userId AND w.balancePaise >= :amount")
    int deductBalance(@Param("userId") UUID userId, @Param("amount") long amount);

    @Modifying
    @Query("UPDATE Wallet w SET w.balancePaise = w.balancePaise + :amount WHERE w.user.id = :userId")
    int addBalance(@Param("userId") UUID userId, @Param("amount") long amount);
}
