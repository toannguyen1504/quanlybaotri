CREATE TABLE departments (
    id UUID PRIMARY KEY, code VARCHAR(50) NOT NULL UNIQUE, name VARCHAR(150) NOT NULL,
    description VARCHAR(500), location VARCHAR(255), contact_email VARCHAR(190), contact_phone VARCHAR(30),
    active BOOLEAN NOT NULL, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE roles (id UUID PRIMARY KEY, name VARCHAR(30) NOT NULL UNIQUE, description VARCHAR(255));
INSERT INTO roles(id,name,description) VALUES
('00000000-0000-0000-0000-000000000001','REQUESTER','Projection'),
('00000000-0000-0000-0000-000000000002','TECHNICIAN','Projection'),
('00000000-0000-0000-0000-000000000003','MANAGER','Projection'),
('00000000-0000-0000-0000-000000000004','ADMIN','Projection');
CREATE TABLE users (
    id UUID PRIMARY KEY, username VARCHAR(80) NOT NULL UNIQUE, email VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL, full_name VARCHAR(150) NOT NULL, phone VARCHAR(30),
    department_id UUID REFERENCES departments(id), enabled BOOLEAN NOT NULL, must_change_password BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE user_roles (user_id UUID NOT NULL REFERENCES users(id), role_id UUID NOT NULL REFERENCES roles(id), PRIMARY KEY(user_id, role_id));
CREATE TABLE equipment_categories (
    id UUID PRIMARY KEY, code VARCHAR(50) NOT NULL UNIQUE, name VARCHAR(150) NOT NULL, description VARCHAR(500), active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE equipment (
    id UUID PRIMARY KEY, asset_code VARCHAR(80) NOT NULL UNIQUE, name VARCHAR(200) NOT NULL,
    serial_number VARCHAR(150), manufacturer VARCHAR(150), model VARCHAR(150), location VARCHAR(255),
    category_id UUID NOT NULL REFERENCES equipment_categories(id), department_id UUID REFERENCES departments(id),
    status VARCHAR(30) NOT NULL, active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE tickets (
    id UUID PRIMARY KEY, code VARCHAR(30) NOT NULL UNIQUE, equipment_id UUID NOT NULL REFERENCES equipment(id),
    requester_id UUID NOT NULL REFERENCES users(id), assignee_id UUID REFERENCES users(id), title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL, priority VARCHAR(20) NOT NULL, status VARCHAR(30) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL, response_due_at TIMESTAMPTZ NOT NULL, resolution_due_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ, started_at TIMESTAMPTZ, resolved_at TIMESTAMPTZ, closed_at TIMESTAMPTZ,
    resolution_summary TEXT, charge_type VARCHAR(10) NOT NULL, parts_cost NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE parts (
    id UUID PRIMARY KEY, code VARCHAR(80) NOT NULL UNIQUE, name VARCHAR(200) NOT NULL, unit VARCHAR(50) NOT NULL,
    current_stock NUMERIC(19,3) NOT NULL DEFAULT 0, minimum_stock NUMERIC(19,3) NOT NULL DEFAULT 0,
    default_unit_cost NUMERIC(19,2) NOT NULL DEFAULT 0, active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE ticket_parts (
    id UUID PRIMARY KEY, ticket_id UUID NOT NULL REFERENCES tickets(id), part_id UUID NOT NULL REFERENCES parts(id),
    quantity NUMERIC(19,3) NOT NULL, unit_cost NUMERIC(19,2) NOT NULL,
    used_by_id UUID NOT NULL REFERENCES users(id), used_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE stock_movements (
    id UUID PRIMARY KEY, part_id UUID NOT NULL REFERENCES parts(id), ticket_id UUID REFERENCES tickets(id),
    actor_id UUID NOT NULL REFERENCES users(id), movement_type VARCHAR(20) NOT NULL,
    quantity NUMERIC(19,3) NOT NULL, balance_after NUMERIC(19,3) NOT NULL,
    unit_cost NUMERIC(19,2), reason VARCHAR(500) NOT NULL, created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_stock_movements_part ON stock_movements(part_id, created_at);
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY, aggregate_type VARCHAR(50) NOT NULL, aggregate_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL, routing_key VARCHAR(100) NOT NULL, payload_json TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL, published_at TIMESTAMPTZ, attempts INTEGER NOT NULL DEFAULT 0, last_error VARCHAR(1000)
);
CREATE INDEX idx_outbox_unpublished ON outbox_events(occurred_at) WHERE published_at IS NULL;
CREATE TABLE processed_events (event_id UUID PRIMARY KEY, event_type VARCHAR(120) NOT NULL, processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
