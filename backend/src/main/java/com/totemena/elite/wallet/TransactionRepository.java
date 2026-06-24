package com.totemena.elite.wallet;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    Page<WalletTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<WalletTransaction> findByUserIdAndTypeOrderByCreatedAtDesc(UUID userId, String type, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(t.amountPaise), 0) FROM WalletTransaction t WHERE t.user.id = :userId AND t.type = :type AND EXTRACT(MONTH FROM t.createdAt) = EXTRACT(MONTH FROM CURRENT_DATE) AND EXTRACT(YEAR FROM t.createdAt) = EXTRACT(YEAR FROM CURRENT_DATE)")
    Long sumAmountByUserIdAndTypeThisMonth(UUID userId, String type);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE WalletTransaction t SET t.referenceId = :betId WHERE t.id = :txId")
    void updateReferenceId(@org.springframework.data.repository.query.Param("txId") UUID txId, @org.springframework.data.repository.query.Param("betId") UUID betId);
}
