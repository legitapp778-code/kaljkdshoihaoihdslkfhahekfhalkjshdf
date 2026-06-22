-- V5: Add performance indexes to resolve full table scan timeouts

-- Index for global stats (sumBetsSince, maxPayoutSince)
CREATE INDEX idx_bets_created_at ON bets(created_at);

-- Index for user stats transactions (depositedThisMonth, withdrawnThisMonth)
CREATE INDEX idx_transactions_user_type_date ON transactions(user_id, type, created_at);

-- Index for global history (findRecentFinishedRounds)
CREATE INDEX idx_rounds_finished ON rounds(finished_at);
