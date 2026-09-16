ALTER TABLE tickets
    DROP CONSTRAINT chk_tickets_charge_type;

ALTER TABLE tickets
    ALTER COLUMN charge_type SET DEFAULT 'PENDING';

UPDATE tickets
SET charge_type = 'PENDING'
WHERE status NOT IN ('CLOSED', 'REJECTED', 'CANCELLED');

ALTER TABLE tickets
    ADD CONSTRAINT chk_tickets_charge_type CHECK (charge_type IN ('PENDING', 'FREE', 'PAID'));
