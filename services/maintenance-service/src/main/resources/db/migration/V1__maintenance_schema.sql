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
    id UUID PRIMARY KEY, code VARCHAR(50) NOT NULL UNIQUE, name VARCHAR(150) NOT NULL, description VARCHAR(500),
    active BOOLEAN NOT NULL, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE equipment (
    id UUID PRIMARY KEY, asset_code VARCHAR(80) NOT NULL UNIQUE, name VARCHAR(200) NOT NULL,
    serial_number VARCHAR(150), manufacturer VARCHAR(150), model VARCHAR(150), location VARCHAR(255),
    category_id UUID NOT NULL REFERENCES equipment_categories(id), department_id UUID REFERENCES departments(id),
    status VARCHAR(30) NOT NULL, active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE sla_policies (
    id UUID PRIMARY KEY, priority VARCHAR(20) NOT NULL UNIQUE, response_minutes INTEGER NOT NULL,
    resolution_minutes INTEGER NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE SEQUENCE ticket_number_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE tickets (
    id UUID PRIMARY KEY, code VARCHAR(30) NOT NULL UNIQUE,
    equipment_id UUID NOT NULL REFERENCES equipment(id), requester_id UUID NOT NULL REFERENCES users(id),
    assignee_id UUID REFERENCES users(id), title VARCHAR(200) NOT NULL, description TEXT NOT NULL,
    priority VARCHAR(20) NOT NULL, status VARCHAR(30) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL, response_due_at TIMESTAMPTZ NOT NULL, resolution_due_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ, started_at TIMESTAMPTZ, resolved_at TIMESTAMPTZ, closed_at TIMESTAMPTZ,
    resolution_summary TEXT, charge_type VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    parts_cost NUMERIC(19,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_tickets_charge_type CHECK(charge_type IN ('PENDING','FREE','PAID')),
    CONSTRAINT chk_tickets_parts_cost CHECK(parts_cost >= 0)
);
CREATE INDEX idx_tickets_requester ON tickets(requester_id);
CREATE INDEX idx_tickets_assignee ON tickets(assignee_id);
CREATE INDEX idx_tickets_status_priority ON tickets(status, priority);
CREATE TABLE ticket_assignment_history (
    id UUID PRIMARY KEY, ticket_id UUID NOT NULL REFERENCES tickets(id), technician_id UUID NOT NULL REFERENCES users(id),
    assigned_by_id UUID NOT NULL REFERENCES users(id), assigned_at TIMESTAMPTZ NOT NULL,
    unassigned_at TIMESTAMPTZ, reason VARCHAR(500)
);
CREATE TABLE work_logs (
    id UUID PRIMARY KEY, ticket_id UUID NOT NULL REFERENCES tickets(id), technician_id UUID NOT NULL REFERENCES users(id),
    content TEXT NOT NULL, minutes_spent INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE ticket_events (
    id UUID PRIMARY KEY, ticket_id UUID NOT NULL REFERENCES tickets(id), actor_id UUID REFERENCES users(id),
    event_type VARCHAR(50) NOT NULL, from_status VARCHAR(30), to_status VARCHAR(30),
    description TEXT NOT NULL, metadata_json TEXT, created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_ticket_events_timeline ON ticket_events(ticket_id, created_at);
CREATE TABLE attachments (
    id UUID PRIMARY KEY, ticket_id UUID NOT NULL REFERENCES tickets(id), work_log_id UUID REFERENCES work_logs(id),
    uploaded_by_id UUID NOT NULL REFERENCES users(id), storage_key VARCHAR(255) NOT NULL UNIQUE,
    original_name VARCHAR(255) NOT NULL, content_type VARCHAR(100) NOT NULL, file_size BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL, created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY, aggregate_type VARCHAR(50) NOT NULL, aggregate_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL, routing_key VARCHAR(100) NOT NULL, payload_json TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL, published_at TIMESTAMPTZ, attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000)
);
CREATE INDEX idx_outbox_unpublished ON outbox_events(occurred_at) WHERE published_at IS NULL;
CREATE TABLE processed_events (event_id UUID PRIMARY KEY, event_type VARCHAR(120) NOT NULL, processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);

INSERT INTO sla_policies (id, priority, response_minutes, resolution_minutes, active, updated_at, version) VALUES
('10000000-0000-0000-0000-000000000001', 'CRITICAL', 15, 240, TRUE, CURRENT_TIMESTAMP, 0),
('10000000-0000-0000-0000-000000000002', 'HIGH', 60, 480, TRUE, CURRENT_TIMESTAMP, 0),
('10000000-0000-0000-0000-000000000003', 'MEDIUM', 240, 1440, TRUE, CURRENT_TIMESTAMP, 0),
('10000000-0000-0000-0000-000000000004', 'LOW', 480, 4320, TRUE, CURRENT_TIMESTAMP, 0);
