ALTER TABLE users DROP CONSTRAINT IF EXISTS users_department_id_fkey;
DROP TABLE IF EXISTS departments;
DROP TABLE IF EXISTS outbox_events;
