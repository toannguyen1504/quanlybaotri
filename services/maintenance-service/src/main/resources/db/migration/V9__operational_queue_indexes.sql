CREATE INDEX IF NOT EXISTS idx_tickets_submitted_response_due
    ON tickets(response_due_at)
    WHERE status = 'SUBMITTED';

CREATE INDEX IF NOT EXISTS idx_ticket_cost_summaries_status
    ON ticket_cost_summaries(status, ticket_id);
