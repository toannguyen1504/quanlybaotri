CREATE INDEX IF NOT EXISTS idx_ticket_costs_status_accepted ON ticket_cost_summaries (status, accepted_at DESC);
CREATE INDEX IF NOT EXISTS idx_tickets_payment_scope ON tickets (requester_id, assignee_id, submitted_at DESC);
