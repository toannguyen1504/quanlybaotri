ALTER TABLE attachments ADD COLUMN attachment_type VARCHAR(30);
UPDATE attachments SET attachment_type = CASE WHEN cost_item_id IS NOT NULL THEN 'INVOICE' ELSE 'OTHER' END;
ALTER TABLE attachments ALTER COLUMN attachment_type SET NOT NULL;
ALTER TABLE attachments ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE attachments ADD COLUMN deleted_by_id UUID;
CREATE INDEX idx_attachments_ticket_active ON attachments(ticket_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_attachments_delete_pending ON attachments(deleted_at) WHERE deleted_at IS NOT NULL;
