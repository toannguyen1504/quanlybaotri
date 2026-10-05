ALTER TABLE tickets RENAME COLUMN branch_id TO customer_id;
ALTER TABLE tickets RENAME COLUMN department_id TO site_id;
ALTER INDEX idx_tickets_branch RENAME TO idx_tickets_customer;
ALTER INDEX idx_tickets_department RENAME TO idx_tickets_site;

ALTER TABLE tickets ADD COLUMN created_by_id UUID;
UPDATE tickets SET created_by_id = requester_id WHERE created_by_id IS NULL;
ALTER TABLE tickets ALTER COLUMN created_by_id SET NOT NULL;

ALTER TABLE tickets ADD COLUMN warranty_status_at_creation VARCHAR(30);
UPDATE tickets SET warranty_status_at_creation = 'UNKNOWN' WHERE warranty_status_at_creation IS NULL;
ALTER TABLE tickets ALTER COLUMN warranty_status_at_creation SET NOT NULL;
ALTER TABLE tickets ADD COLUMN warranty_end_at_creation TIMESTAMPTZ;
ALTER TABLE tickets ADD CONSTRAINT chk_ticket_warranty_status
    CHECK (warranty_status_at_creation IN ('IN_WARRANTY','OUT_OF_WARRANTY','UNKNOWN'));
