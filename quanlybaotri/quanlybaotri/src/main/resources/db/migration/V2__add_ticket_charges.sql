ALTER TABLE tickets
    ADD COLUMN charge_type VARCHAR(10) NOT NULL DEFAULT 'FREE',
    ADD COLUMN parts_cost NUMERIC(19,2) NOT NULL DEFAULT 0;

UPDATE tickets t
SET parts_cost = COALESCE((
    SELECT SUM(tp.quantity * tp.unit_cost)
    FROM ticket_parts tp
    WHERE tp.ticket_id = t.id
), 0);

ALTER TABLE tickets
    ADD CONSTRAINT chk_tickets_charge_type CHECK (charge_type IN ('FREE', 'PAID')),
    ADD CONSTRAINT chk_tickets_parts_cost CHECK (parts_cost >= 0);
