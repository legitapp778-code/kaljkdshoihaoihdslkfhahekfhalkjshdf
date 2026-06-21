-- V1: Users & Wallets
CREATE TABLE users (
    id            VARCHAR(36) PRIMARY KEY,
    phone         VARCHAR(15) NOT NULL UNIQUE,
    email         VARCHAR(100),
    display_name  VARCHAR(50),
    kyc_status    VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    is_active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE TABLE wallets (
    id              VARCHAR(36) PRIMARY KEY,
    user_id         VARCHAR(36) NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    balance_paise   BIGINT NOT NULL DEFAULT 0 CHECK (balance_paise >= 0),
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);
