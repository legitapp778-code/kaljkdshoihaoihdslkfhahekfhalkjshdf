-- V6: Login History table for tracking user login devices and IP addresses
CREATE TABLE login_history (
    id           VARCHAR(36) PRIMARY KEY,
    user_id      VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_name  VARCHAR(150),
    ip_address   VARCHAR(64),
    user_agent   VARCHAR(500),
    logged_in_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE INDEX idx_login_history_user_id ON login_history(user_id);
