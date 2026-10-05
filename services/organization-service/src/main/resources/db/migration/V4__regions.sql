CREATE TABLE regions (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE customer_sites
    ADD COLUMN region_id UUID REFERENCES regions(id);

CREATE INDEX idx_customer_sites_region ON customer_sites(region_id);
