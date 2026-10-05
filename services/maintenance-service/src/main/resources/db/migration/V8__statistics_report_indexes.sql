CREATE INDEX IF NOT EXISTS idx_tickets_resolved_at
    ON tickets(resolved_at)
    WHERE resolved_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tickets_open_resolution_due
    ON tickets(resolution_due_at)
    WHERE resolved_at IS NULL
      AND status IN ('SUBMITTED','ACCEPTED','ASSIGNED','IN_PROGRESS','WAITING_PARTS');
