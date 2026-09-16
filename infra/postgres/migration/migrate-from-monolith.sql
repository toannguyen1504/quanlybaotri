\set ON_ERROR_STOP on

\if :{?source_host}
\else
  \set source_host 'host.docker.internal'
\endif
\if :{?source_port}
\else
  \set source_port '5432'
\endif
\if :{?source_database}
\else
  \set source_database 'quanlybaotri'
\endif
\if :{?source_user}
\else
  \set source_user 'postgres'
\endif
\if :{?source_password}
\else
  \echo 'source_password is required'
  \quit 2
\endif
\if :{?source_ticket_sequence}
\else
  \echo 'source_ticket_sequence is required'
  \quit 2
\endif

-- Run only after Flyway has brought every target database to its latest schema.
-- This script deliberately imports only domain-owned tables. Legacy audit_logs
-- must be exported and checksummed before cutover; they are not a runtime table.

\echo 'Migrating identity_db'
\connect identity_db
CREATE EXTENSION IF NOT EXISTS postgres_fdw;
DROP SCHEMA IF EXISTS monolith_import CASCADE;
DROP SERVER IF EXISTS monolith_source CASCADE;
CREATE SERVER monolith_source FOREIGN DATA WRAPPER postgres_fdw
    OPTIONS (host :'source_host', port :'source_port', dbname :'source_database');
CREATE USER MAPPING FOR CURRENT_USER SERVER monolith_source
    OPTIONS (user :'source_user', password :'source_password');
CREATE SCHEMA monolith_import;
IMPORT FOREIGN SCHEMA public LIMIT TO (users, user_roles)
    FROM SERVER monolith_source INTO monolith_import;

BEGIN;
TRUNCATE TABLE refresh_tokens, user_roles, users CASCADE;
INSERT INTO users (
    id, username, email, password_hash, full_name, phone, department_id,
    enabled, must_change_password, created_at, updated_at, version
)
SELECT id, username, email, password_hash, full_name, phone, department_id,
       enabled, must_change_password, created_at, updated_at, version
FROM monolith_import.users;
INSERT INTO user_roles (user_id, role_id)
SELECT user_id, role_id FROM monolith_import.user_roles;
COMMIT;
DROP SCHEMA monolith_import CASCADE;
DROP SERVER monolith_source CASCADE;

\echo 'Migrating organization_db'
\connect organization_db
CREATE EXTENSION IF NOT EXISTS postgres_fdw;
DROP SCHEMA IF EXISTS monolith_import CASCADE;
DROP SERVER IF EXISTS monolith_source CASCADE;
CREATE SERVER monolith_source FOREIGN DATA WRAPPER postgres_fdw
    OPTIONS (host :'source_host', port :'source_port', dbname :'source_database');
CREATE USER MAPPING FOR CURRENT_USER SERVER monolith_source
    OPTIONS (user :'source_user', password :'source_password');
CREATE SCHEMA monolith_import;
IMPORT FOREIGN SCHEMA public LIMIT TO (departments)
    FROM SERVER monolith_source INTO monolith_import;

BEGIN;
TRUNCATE TABLE departments;
INSERT INTO departments (
    id, code, name, description, location, contact_email, contact_phone,
    active, created_at, updated_at, version
)
SELECT id, code, name, description, location, contact_email, contact_phone,
       active, created_at, updated_at, version
FROM monolith_import.departments;
COMMIT;
DROP SCHEMA monolith_import CASCADE;
DROP SERVER monolith_source CASCADE;

\echo 'Migrating asset_db'
\connect asset_db
CREATE EXTENSION IF NOT EXISTS postgres_fdw;
DROP SCHEMA IF EXISTS monolith_import CASCADE;
DROP SERVER IF EXISTS monolith_source CASCADE;
CREATE SERVER monolith_source FOREIGN DATA WRAPPER postgres_fdw
    OPTIONS (host :'source_host', port :'source_port', dbname :'source_database');
CREATE USER MAPPING FOR CURRENT_USER SERVER monolith_source
    OPTIONS (user :'source_user', password :'source_password');
CREATE SCHEMA monolith_import;
IMPORT FOREIGN SCHEMA public LIMIT TO (equipment_categories, equipment)
    FROM SERVER monolith_source INTO monolith_import;

BEGIN;
TRUNCATE TABLE processed_events, equipment, equipment_categories CASCADE;
INSERT INTO equipment_categories (
    id, code, name, description, active, created_at, updated_at, version
)
SELECT id, code, name, description, active, created_at, updated_at, version
FROM monolith_import.equipment_categories;
INSERT INTO equipment (
    id, asset_code, name, serial_number, manufacturer, model, location,
    category_id, department_id, status, active, created_at, updated_at, version
)
SELECT id, asset_code, name, serial_number, manufacturer, model, location,
       category_id, department_id, status, active, created_at, updated_at, version
FROM monolith_import.equipment;
COMMIT;
DROP SCHEMA monolith_import CASCADE;
DROP SERVER monolith_source CASCADE;

\echo 'Migrating maintenance_db'
\connect maintenance_db
CREATE EXTENSION IF NOT EXISTS postgres_fdw;
DROP SCHEMA IF EXISTS monolith_import CASCADE;
DROP SERVER IF EXISTS monolith_source CASCADE;
CREATE SERVER monolith_source FOREIGN DATA WRAPPER postgres_fdw
    OPTIONS (host :'source_host', port :'source_port', dbname :'source_database');
CREATE USER MAPPING FOR CURRENT_USER SERVER monolith_source
    OPTIONS (user :'source_user', password :'source_password');
CREATE SCHEMA monolith_import;
IMPORT FOREIGN SCHEMA public LIMIT TO (
    sla_policies, tickets, ticket_assignment_history, work_logs, ticket_events, attachments
) FROM SERVER monolith_source INTO monolith_import;

BEGIN;
TRUNCATE TABLE outbox_events, processed_events, attachments, ticket_events,
    work_logs, ticket_assignment_history, tickets, sla_policies CASCADE;
INSERT INTO sla_policies (
    id, priority, response_minutes, resolution_minutes, active, updated_at, version
)
SELECT id, priority, response_minutes, resolution_minutes, active, updated_at, version
FROM monolith_import.sla_policies;
INSERT INTO tickets (
    id, code, equipment_id, requester_id, assignee_id, title, description,
    priority, status, submitted_at, response_due_at, resolution_due_at,
    accepted_at, started_at, resolved_at, closed_at, resolution_summary,
    charge_type, parts_cost, created_at, updated_at, version
)
SELECT id, code, equipment_id, requester_id, assignee_id, title, description,
       priority, status, submitted_at, response_due_at, resolution_due_at,
       accepted_at, started_at, resolved_at, closed_at, resolution_summary,
       charge_type, parts_cost, created_at, updated_at, version
FROM monolith_import.tickets;
INSERT INTO ticket_assignment_history (
    id, ticket_id, technician_id, assigned_by_id, assigned_at, unassigned_at, reason
)
SELECT id, ticket_id, technician_id, assigned_by_id, assigned_at, unassigned_at, reason
FROM monolith_import.ticket_assignment_history;
INSERT INTO work_logs (
    id, ticket_id, technician_id, content, minutes_spent, created_at, updated_at, version
)
SELECT id, ticket_id, technician_id, content, minutes_spent, created_at, updated_at, version
FROM monolith_import.work_logs;
INSERT INTO ticket_events (
    id, ticket_id, actor_id, event_type, from_status, to_status,
    description, metadata_json, created_at
)
SELECT id, ticket_id, actor_id, event_type, from_status, to_status,
       description, metadata_json, created_at
FROM monolith_import.ticket_events;
INSERT INTO attachments (
    id, ticket_id, work_log_id, uploaded_by_id, storage_key, original_name,
    content_type, file_size, sha256, created_at
)
SELECT id, ticket_id, work_log_id, uploaded_by_id, storage_key, original_name,
       content_type, file_size, sha256, created_at
FROM monolith_import.attachments;
SELECT setval('ticket_number_seq', :source_ticket_sequence, true);
COMMIT;
DROP SCHEMA monolith_import CASCADE;
DROP SERVER monolith_source CASCADE;

\echo 'Migrating inventory_db'
\connect inventory_db
CREATE EXTENSION IF NOT EXISTS postgres_fdw;
DROP SCHEMA IF EXISTS monolith_import CASCADE;
DROP SERVER IF EXISTS monolith_source CASCADE;
CREATE SERVER monolith_source FOREIGN DATA WRAPPER postgres_fdw
    OPTIONS (host :'source_host', port :'source_port', dbname :'source_database');
CREATE USER MAPPING FOR CURRENT_USER SERVER monolith_source
    OPTIONS (user :'source_user', password :'source_password');
CREATE SCHEMA monolith_import;
IMPORT FOREIGN SCHEMA public LIMIT TO (parts, ticket_parts, stock_movements)
    FROM SERVER monolith_source INTO monolith_import;

BEGIN;
TRUNCATE TABLE outbox_events, stock_movements, ticket_parts, parts CASCADE;
INSERT INTO parts (
    id, code, name, unit, current_stock, minimum_stock, default_unit_cost,
    active, created_at, updated_at, version
)
SELECT id, code, name, unit, current_stock, minimum_stock, default_unit_cost,
       active, created_at, updated_at, version
FROM monolith_import.parts;
INSERT INTO ticket_parts (
    id, ticket_id, part_id, quantity, unit_cost, used_by_id, used_at
)
SELECT id, ticket_id, part_id, quantity, unit_cost, used_by_id, used_at
FROM monolith_import.ticket_parts;
INSERT INTO stock_movements (
    id, part_id, ticket_id, actor_id, movement_type, quantity, balance_after,
    unit_cost, reason, created_at
)
SELECT id, part_id, ticket_id, actor_id, movement_type, quantity, balance_after,
       unit_cost, reason, created_at
FROM monolith_import.stock_movements;
COMMIT;
DROP SCHEMA monolith_import CASCADE;
DROP SERVER monolith_source CASCADE;

\echo 'Migrating notification_db'
\connect notification_db
CREATE EXTENSION IF NOT EXISTS postgres_fdw;
DROP SCHEMA IF EXISTS monolith_import CASCADE;
DROP SERVER IF EXISTS monolith_source CASCADE;
CREATE SERVER monolith_source FOREIGN DATA WRAPPER postgres_fdw
    OPTIONS (host :'source_host', port :'source_port', dbname :'source_database');
CREATE USER MAPPING FOR CURRENT_USER SERVER monolith_source
    OPTIONS (user :'source_user', password :'source_password');
CREATE SCHEMA monolith_import;
IMPORT FOREIGN SCHEMA public LIMIT TO (notifications)
    FROM SERVER monolith_source INTO monolith_import;

BEGIN;
TRUNCATE TABLE processed_messages, notifications;
INSERT INTO notifications (
    id, user_id, event_id, type, title, message, reference_type, reference_id,
    read_at, created_at
)
SELECT id, user_id, event_id, type, title, message, reference_type, reference_id,
       read_at, created_at
FROM monolith_import.notifications;
COMMIT;
DROP SCHEMA monolith_import CASCADE;
DROP SERVER monolith_source CASCADE;

\echo 'Migration completed successfully without cross-service projections'
