ALTER TABLE equipment DROP CONSTRAINT IF EXISTS equipment_department_id_fkey;
DROP TABLE IF EXISTS departments;
DROP TABLE IF EXISTS outbox_events;
