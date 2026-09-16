CREATE TABLE departments (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE roles (
    id UUID PRIMARY KEY,
    name VARCHAR(30) NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    email VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(30),
    department_id UUID REFERENCES departments(id),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id),
    role_id UUID NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by_hash VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

CREATE TABLE equipment_categories (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE equipment (
    id UUID PRIMARY KEY,
    asset_code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    serial_number VARCHAR(150),
    manufacturer VARCHAR(150),
    model VARCHAR(150),
    location VARCHAR(255),
    category_id UUID NOT NULL REFERENCES equipment_categories(id),
    department_id UUID REFERENCES departments(id),
    status VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_equipment_category ON equipment(category_id);
CREATE INDEX idx_equipment_department ON equipment(department_id);

CREATE TABLE sla_policies (
    id UUID PRIMARY KEY,
    priority VARCHAR(20) NOT NULL UNIQUE,
    response_minutes INTEGER NOT NULL,
    resolution_minutes INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE SEQUENCE ticket_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE tickets (
    id UUID PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    equipment_id UUID NOT NULL REFERENCES equipment(id),
    requester_id UUID NOT NULL REFERENCES users(id),
    assignee_id UUID REFERENCES users(id),
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    response_due_at TIMESTAMPTZ NOT NULL,
    resolution_due_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    resolution_summary TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_tickets_requester ON tickets(requester_id);
CREATE INDEX idx_tickets_assignee ON tickets(assignee_id);
CREATE INDEX idx_tickets_status_priority ON tickets(status, priority);
CREATE INDEX idx_tickets_sla ON tickets(resolution_due_at) WHERE status NOT IN ('CLOSED','REJECTED','CANCELLED');

CREATE TABLE ticket_assignment_history (
    id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES tickets(id),
    technician_id UUID NOT NULL REFERENCES users(id),
    assigned_by_id UUID NOT NULL REFERENCES users(id),
    assigned_at TIMESTAMPTZ NOT NULL,
    unassigned_at TIMESTAMPTZ,
    reason VARCHAR(500)
);
CREATE INDEX idx_assignment_ticket ON ticket_assignment_history(ticket_id, assigned_at);

CREATE TABLE work_logs (
    id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES tickets(id),
    technician_id UUID NOT NULL REFERENCES users(id),
    content TEXT NOT NULL,
    minutes_spent INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE ticket_events (
    id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES tickets(id),
    actor_id UUID REFERENCES users(id),
    event_type VARCHAR(50) NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30),
    description TEXT NOT NULL,
    metadata_json TEXT,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_ticket_events_timeline ON ticket_events(ticket_id, created_at);

CREATE TABLE parts (
    id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    current_stock NUMERIC(19,3) NOT NULL DEFAULT 0,
    minimum_stock NUMERIC(19,3) NOT NULL DEFAULT 0,
    default_unit_cost NUMERIC(19,2) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE ticket_parts (
    id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES tickets(id),
    part_id UUID NOT NULL REFERENCES parts(id),
    quantity NUMERIC(19,3) NOT NULL,
    unit_cost NUMERIC(19,2) NOT NULL,
    used_by_id UUID NOT NULL REFERENCES users(id),
    used_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE stock_movements (
    id UUID PRIMARY KEY,
    part_id UUID NOT NULL REFERENCES parts(id),
    ticket_id UUID REFERENCES tickets(id),
    actor_id UUID NOT NULL REFERENCES users(id),
    movement_type VARCHAR(20) NOT NULL,
    quantity NUMERIC(19,3) NOT NULL,
    balance_after NUMERIC(19,3) NOT NULL,
    unit_cost NUMERIC(19,2),
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_stock_movements_part ON stock_movements(part_id, created_at);

CREATE TABLE attachments (
    id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES tickets(id),
    work_log_id UUID REFERENCES work_logs(id),
    uploaded_by_id UUID NOT NULL REFERENCES users(id),
    storage_key VARCHAR(255) NOT NULL UNIQUE,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    event_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    reference_type VARCHAR(50),
    reference_id UUID,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_notification_event_user UNIQUE(event_id, user_id)
);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, read_at, created_at);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    routing_key VARCHAR(100) NOT NULL,
    payload_json TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000)
);
CREATE INDEX idx_outbox_unpublished ON outbox_events(occurred_at) WHERE published_at IS NULL;

CREATE TABLE processed_messages (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor_id UUID REFERENCES users(id),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID,
    details_json TEXT,
    ip_address VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_audit_logs_created ON audit_logs(created_at);

INSERT INTO roles (id, name, description) VALUES
('00000000-0000-0000-0000-000000000001', 'REQUESTER', 'Creates and follows maintenance tickets'),
('00000000-0000-0000-0000-000000000002', 'TECHNICIAN', 'Processes assigned maintenance tickets'),
('00000000-0000-0000-0000-000000000003', 'MANAGER', 'Accepts, assigns and manages maintenance work'),
('00000000-0000-0000-0000-000000000004', 'ADMIN', 'Administers the complete system');

INSERT INTO sla_policies (id, priority, response_minutes, resolution_minutes, active, updated_at, version) VALUES
('10000000-0000-0000-0000-000000000001', 'CRITICAL', 15, 240, TRUE, CURRENT_TIMESTAMP, 0),
('10000000-0000-0000-0000-000000000002', 'HIGH', 60, 480, TRUE, CURRENT_TIMESTAMP, 0),
('10000000-0000-0000-0000-000000000003', 'MEDIUM', 240, 1440, TRUE, CURRENT_TIMESTAMP, 0),
('10000000-0000-0000-0000-000000000004', 'LOW', 480, 4320, TRUE, CURRENT_TIMESTAMP, 0);
