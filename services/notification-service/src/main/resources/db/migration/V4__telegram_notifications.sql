CREATE TABLE telegram_links (
    user_id UUID PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL UNIQUE,
    telegram_chat_id BIGINT NOT NULL UNIQUE,
    telegram_username VARCHAR(64),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    linked_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE telegram_link_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_telegram_link_tokens_expiry ON telegram_link_tokens(expires_at);

CREATE TABLE telegram_update_state (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    last_update_id BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
INSERT INTO telegram_update_state(id, last_update_id, updated_at)
VALUES (1, 0, CURRENT_TIMESTAMP);

CREATE TABLE telegram_deliveries (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL UNIQUE REFERENCES notifications(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    message_text VARCHAR(2000) NOT NULL,
    reference_id UUID,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    telegram_message_id BIGINT,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ
);
CREATE INDEX idx_telegram_deliveries_pending
    ON telegram_deliveries(status, next_attempt_at, created_at);
CREATE INDEX idx_telegram_deliveries_user ON telegram_deliveries(user_id);
