ALTER TABLE tickets ADD COLUMN creation_idempotency_key VARCHAR(128);
ALTER TABLE tickets ADD COLUMN creation_actor_id UUID;
ALTER TABLE tickets ADD COLUMN creation_request_hash VARCHAR(64);

CREATE UNIQUE INDEX uq_tickets_creation_idempotency_key
    ON tickets(creation_idempotency_key)
    WHERE creation_idempotency_key IS NOT NULL;
