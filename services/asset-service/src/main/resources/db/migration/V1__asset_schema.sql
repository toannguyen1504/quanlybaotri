CREATE TABLE departments (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    location VARCHAR(255),
    contact_email VARCHAR(190),
    contact_phone VARCHAR(30),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

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

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,aggregate_type VARCHAR(50) NOT NULL,aggregate_id UUID NOT NULL,event_type VARCHAR(80) NOT NULL,
    routing_key VARCHAR(100) NOT NULL,payload_json TEXT NOT NULL,occurred_at TIMESTAMPTZ NOT NULL,published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,last_error VARCHAR(1000)
);
CREATE INDEX idx_outbox_unpublished ON outbox_events(occurred_at) WHERE published_at IS NULL;
CREATE TABLE processed_events(event_id UUID PRIMARY KEY,event_type VARCHAR(120) NOT NULL,processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
