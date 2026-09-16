ALTER TABLE ticket_parts DROP CONSTRAINT IF EXISTS ticket_parts_ticket_id_fkey;
ALTER TABLE ticket_parts DROP CONSTRAINT IF EXISTS ticket_parts_used_by_id_fkey;
ALTER TABLE stock_movements DROP CONSTRAINT IF EXISTS stock_movements_ticket_id_fkey;
ALTER TABLE stock_movements DROP CONSTRAINT IF EXISTS stock_movements_actor_id_fkey;

DROP TABLE IF EXISTS tickets;
DROP TABLE IF EXISTS equipment;
DROP TABLE IF EXISTS equipment_categories;
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS departments;
DROP TABLE IF EXISTS processed_events;
