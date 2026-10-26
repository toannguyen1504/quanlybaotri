CREATE TABLE telegram_command_receipts (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    operation VARCHAR(60) NOT NULL,
    result_id UUID,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_inventory_telegram_commands_created
    ON telegram_command_receipts(created_at);
