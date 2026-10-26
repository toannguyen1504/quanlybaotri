CREATE INDEX IF NOT EXISTS idx_payment_attempts_status_created ON payment_attempts (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payment_attempts_ticket_created ON payment_attempts (ticket_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payment_attempts_initiator_created ON payment_attempts (initiated_by_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payment_attempts_expires_pending ON payment_attempts (expires_at) WHERE status = 'PENDING';
