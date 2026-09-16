ALTER TABLE audit_logs
    ADD COLUMN details_json TEXT,
    ADD COLUMN ip_address VARCHAR(64);

CREATE INDEX idx_audit_logs_created ON audit_logs(created_at);
