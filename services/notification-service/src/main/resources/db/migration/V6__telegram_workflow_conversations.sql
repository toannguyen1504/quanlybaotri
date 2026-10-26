CREATE TABLE telegram_conversations (
    user_id UUID PRIMARY KEY,
    ticket_id UUID,
    state VARCHAR(40) NOT NULL,
    draft_json TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_telegram_conversations_expiry
    ON telegram_conversations(expires_at);
