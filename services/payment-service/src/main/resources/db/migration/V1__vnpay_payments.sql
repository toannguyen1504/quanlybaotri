CREATE TABLE payment_attempts (
    id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL,
    txn_ref VARCHAR(100) NOT NULL UNIQUE,
    amount NUMERIC(19,2) NOT NULL,
    initiated_by_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    client_ip VARCHAR(45) NOT NULL,
    provider_transaction_no VARCHAR(32),
    bank_code VARCHAR(20),
    card_type VARCHAR(20),
    response_code VARCHAR(10),
    transaction_status VARCHAR(10),
    pay_date TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    last_reconciled_at TIMESTAMPTZ,
    last_query_response_code VARCHAR(10),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_payment_attempt_status CHECK(status IN ('PENDING','SUCCEEDED','FAILED')),
    CONSTRAINT chk_payment_attempt_amount CHECK(amount > 0)
);

CREATE INDEX idx_payment_attempt_ticket_created
    ON payment_attempts(ticket_id, created_at DESC);
CREATE UNIQUE INDEX uq_payment_attempt_pending_ticket
    ON payment_attempts(ticket_id) WHERE status = 'PENDING';
CREATE UNIQUE INDEX uq_payment_attempt_provider_transaction
    ON payment_attempts(provider_transaction_no)
    WHERE provider_transaction_no IS NOT NULL;

