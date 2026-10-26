ALTER TABLE ticket_cost_summaries DROP CONSTRAINT IF EXISTS chk_ticket_cost_status;

UPDATE ticket_cost_summaries SET status = 'QUOTED' WHERE status = 'READY';
UPDATE ticket_cost_summaries SET status = 'ACCEPTED' WHERE status = 'APPROVED';

ALTER TABLE ticket_cost_summaries RENAME COLUMN submitted_by_id TO quoted_by_id;
ALTER TABLE ticket_cost_summaries RENAME COLUMN submitted_at TO quoted_at;
ALTER TABLE ticket_cost_summaries RENAME COLUMN approved_by_id TO accepted_by_id;
ALTER TABLE ticket_cost_summaries RENAME COLUMN approved_at TO accepted_at;
ALTER TABLE ticket_cost_summaries RENAME COLUMN rejected_by_id TO declined_by_id;
ALTER TABLE ticket_cost_summaries RENAME COLUMN rejected_at TO declined_at;
ALTER TABLE ticket_cost_summaries RENAME COLUMN rejection_reason TO decline_reason;

ALTER TABLE ticket_cost_summaries
    ADD CONSTRAINT chk_ticket_cost_status CHECK(status IN ('DRAFT','QUOTED','ACCEPTED'));
