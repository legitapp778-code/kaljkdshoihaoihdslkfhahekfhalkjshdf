-- V2: Auth tables
CREATE TABLE refresh_tokens (
    id          VARCHAR(36) PRIMARY KEY,
    user_id     VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  DATETIME(6) NOT NULL,
    revoked     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE otp_attempts (
    id            VARCHAR(36) PRIMARY KEY,
    phone         VARCHAR(15) NOT NULL,
    attempt_count INT         NOT NULL DEFAULT 0,
    window_start  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
