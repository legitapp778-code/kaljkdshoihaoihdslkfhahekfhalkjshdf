-- V3: Game tables
CREATE TABLE rounds (
    id                VARCHAR(36) PRIMARY KEY,
    status            VARCHAR(20) NOT NULL DEFAULT 'BETTING',
    winning_row_tota  SMALLINT,
    winning_row_mena  SMALLINT,
    betting_starts_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    spinning_at       DATETIME(6),
    finished_at       DATETIME(6),
    created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE bets (
    id               VARCHAR(36) PRIMARY KEY,
    round_id         VARCHAR(36) NOT NULL REFERENCES rounds(id),
    user_id          VARCHAR(36) NOT NULL REFERENCES users(id),
    bird             VARCHAR(4)  NOT NULL CHECK (bird IN ('tota','mena')),
    selected_row     SMALLINT    NOT NULL CHECK (selected_row BETWEEN 1 AND 5),
    amount_paise     BIGINT      NOT NULL CHECK (amount_paise > 0),
    idempotency_key  VARCHAR(36) NOT NULL,
    payout_paise     BIGINT,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE (round_id, user_id, bird),
    UNIQUE (idempotency_key)
);

CREATE TABLE transactions (
    id                  VARCHAR(36) PRIMARY KEY,
    user_id             VARCHAR(36) NOT NULL REFERENCES users(id),
    type                VARCHAR(20) NOT NULL,
    amount_paise        BIGINT      NOT NULL,
    balance_after_paise BIGINT      NOT NULL,
    reference_id        VARCHAR(36),
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE INDEX idx_bets_round_user   ON bets(round_id, user_id);
CREATE INDEX idx_bets_user         ON bets(user_id);
CREATE INDEX idx_transactions_user ON transactions(user_id);
CREATE INDEX idx_rounds_status     ON rounds(status);
