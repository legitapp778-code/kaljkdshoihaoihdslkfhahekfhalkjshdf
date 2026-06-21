-- ═══════════════════════════════════════════════════════════
-- TOTA & MENA ELITE — Full Database Schema
-- Import this file directly into PostgreSQL:
--   psql -U postgres -d totemena -f full_schema.sql
-- ═══════════════════════════════════════════════════════════

-- Create the database first (run separately if needed):
-- CREATE DATABASE totemena;

-- ── USERS ──
CREATE TABLE IF NOT EXISTS users (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    phone         VARCHAR(15) NOT NULL UNIQUE,
    display_name  VARCHAR(50),
    kyc_status    VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    is_active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── WALLETS ──
CREATE TABLE IF NOT EXISTS wallets (
    id              UUID   PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID   NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    balance_paise   BIGINT NOT NULL DEFAULT 0 CHECK (balance_paise >= 0),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── REFRESH TOKENS ──
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── OTP ATTEMPTS ──
CREATE TABLE IF NOT EXISTS otp_attempts (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    phone         VARCHAR(15) NOT NULL,
    attempt_count INT         NOT NULL DEFAULT 0,
    window_start  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── ROUNDS ──
CREATE TABLE IF NOT EXISTS rounds (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    status            VARCHAR(20) NOT NULL DEFAULT 'BETTING',
    winning_row_tota  SMALLINT,
    winning_row_mena  SMALLINT,
    betting_starts_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    spinning_at       TIMESTAMPTZ,
    finished_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── BETS ──
CREATE TABLE IF NOT EXISTS bets (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    round_id         UUID        NOT NULL REFERENCES rounds(id),
    user_id          UUID        NOT NULL REFERENCES users(id),
    bird             VARCHAR(4)  NOT NULL CHECK (bird IN ('tota','mena')),
    selected_row     SMALLINT    NOT NULL CHECK (selected_row BETWEEN 1 AND 5),
    amount_paise     BIGINT      NOT NULL CHECK (amount_paise > 0),
    idempotency_key  UUID        NOT NULL,
    payout_paise     BIGINT,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (round_id, user_id, bird),
    UNIQUE (idempotency_key)
);

-- ── TRANSACTIONS ──
CREATE TABLE IF NOT EXISTS transactions (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID        NOT NULL REFERENCES users(id),
    type                VARCHAR(20) NOT NULL,
    amount_paise        BIGINT      NOT NULL,
    balance_after_paise BIGINT      NOT NULL,
    reference_id        UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── INDEXES ──
CREATE INDEX IF NOT EXISTS idx_bets_round_user   ON bets(round_id, user_id);
CREATE INDEX IF NOT EXISTS idx_bets_user         ON bets(user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_user ON transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_rounds_status     ON rounds(status);

-- ── FLYWAY SCHEMA HISTORY (so Flyway doesn't fight you) ──
CREATE TABLE IF NOT EXISTS flyway_schema_history (
    installed_rank INT          NOT NULL,
    version        VARCHAR(50),
    description    VARCHAR(200) NOT NULL,
    type           VARCHAR(20)  NOT NULL,
    script         VARCHAR(1000) NOT NULL,
    checksum       INT,
    installed_by   VARCHAR(100) NOT NULL,
    installed_on   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    execution_time INT          NOT NULL,
    success        BOOLEAN      NOT NULL
);
