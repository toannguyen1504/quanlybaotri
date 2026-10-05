ALTER TABLE tickets DROP CONSTRAINT IF EXISTS chk_tickets_charge_type;
ALTER TABLE tickets DROP CONSTRAINT IF EXISTS chk_tickets_parts_cost;
ALTER TABLE tickets DROP COLUMN charge_type;
ALTER TABLE tickets DROP COLUMN parts_cost;
