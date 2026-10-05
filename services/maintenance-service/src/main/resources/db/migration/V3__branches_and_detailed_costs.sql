ALTER TABLE tickets ADD COLUMN branch_id UUID;
ALTER TABLE tickets ADD COLUMN department_id UUID;
UPDATE tickets SET branch_id = '20000000-0000-0000-0000-000000000001' WHERE branch_id IS NULL;
UPDATE tickets SET department_id = '20000000-0000-0000-0000-000000000002' WHERE department_id IS NULL;
ALTER TABLE tickets ALTER COLUMN branch_id SET NOT NULL;
ALTER TABLE tickets ALTER COLUMN department_id SET NOT NULL;
CREATE INDEX idx_tickets_branch ON tickets(branch_id, submitted_at);
CREATE INDEX idx_tickets_department ON tickets(department_id, submitted_at);

CREATE TABLE ticket_cost_summaries (
    ticket_id UUID PRIMARY KEY REFERENCES tickets(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    parts_total NUMERIC(19,2) NOT NULL DEFAULT 0,
    labor_total NUMERIC(19,2) NOT NULL DEFAULT 0,
    outsourced_total NUMERIC(19,2) NOT NULL DEFAULT 0,
    travel_total NUMERIC(19,2) NOT NULL DEFAULT 0,
    other_total NUMERIC(19,2) NOT NULL DEFAULT 0,
    grand_total NUMERIC(19,2) NOT NULL DEFAULT 0,
    submitted_by_id UUID,
    submitted_at TIMESTAMPTZ,
    approved_by_id UUID,
    approved_at TIMESTAMPTZ,
    rejected_by_id UUID,
    rejected_at TIMESTAMPTZ,
    rejection_reason VARCHAR(1000),
    legacy_charge_type VARCHAR(10),
    legacy_parts_cost NUMERIC(19,2),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_ticket_cost_status CHECK(status IN ('DRAFT','READY','APPROVED'))
);

CREATE TABLE ticket_cost_items (
    id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    category VARCHAR(20) NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    source_id UUID,
    calculation_status VARCHAR(20) NOT NULL,
    description VARCHAR(500) NOT NULL,
    quantity NUMERIC(19,3) NOT NULL,
    unit VARCHAR(30) NOT NULL,
    unit_cost NUMERIC(19,2),
    amount NUMERIC(19,2),
    incurred_at TIMESTAMPTZ NOT NULL,
    vendor VARCHAR(200),
    reference_number VARCHAR(100),
    created_by_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_ticket_cost_category CHECK(category IN ('PART','LABOR','OUTSOURCED','TRAVEL','OTHER','LEGACY')),
    CONSTRAINT chk_ticket_cost_source CHECK(source_type IN ('INVENTORY','WORK_LOG','MANUAL','MIGRATION')),
    CONSTRAINT chk_ticket_cost_calculation CHECK(calculation_status IN ('READY','MISSING_RATE')),
    CONSTRAINT chk_ticket_cost_quantity CHECK(quantity > 0),
    CONSTRAINT chk_ticket_cost_amount CHECK(amount IS NULL OR amount >= 0),
    UNIQUE(source_type, source_id)
);
CREATE INDEX idx_ticket_cost_items_ticket ON ticket_cost_items(ticket_id, incurred_at);
CREATE INDEX idx_ticket_cost_items_category ON ticket_cost_items(category, incurred_at);

CREATE TABLE labor_rates (
    id UUID PRIMARY KEY,
    technician_id UUID,
    hourly_rate NUMERIC(19,2) NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_labor_rate_positive CHECK(hourly_rate > 0),
    CONSTRAINT chk_labor_rate_period CHECK(effective_to IS NULL OR effective_to > effective_from)
);
CREATE INDEX idx_labor_rates_lookup ON labor_rates(technician_id, effective_from, effective_to);

ALTER TABLE attachments ADD COLUMN cost_item_id UUID REFERENCES ticket_cost_items(id);
CREATE INDEX idx_attachments_cost_item ON attachments(cost_item_id);

INSERT INTO ticket_cost_summaries (
    ticket_id, status, parts_total, grand_total, legacy_charge_type, legacy_parts_cost, created_at, updated_at
)
SELECT id,
       CASE WHEN status = 'CLOSED' THEN 'READY' ELSE 'DRAFT' END,
       parts_cost,
       parts_cost,
       charge_type,
       parts_cost,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM tickets
ON CONFLICT (ticket_id) DO NOTHING;

INSERT INTO ticket_cost_items (
    id, ticket_id, category, source_type, source_id, calculation_status,
    description, quantity, unit, unit_cost, amount, incurred_at,
    created_at, updated_at, version
)
SELECT (md5(id::text || ':legacy-parts'))::uuid,
       id, 'LEGACY', 'MIGRATION', id, 'READY',
       'Chi phí linh kiện từ hệ thống cũ', 1, 'GÓI', parts_cost, parts_cost,
       COALESCE(closed_at, resolved_at, submitted_at), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM tickets
WHERE parts_cost > 0
ON CONFLICT (source_type, source_id) DO NOTHING;

INSERT INTO ticket_cost_items (
    id, ticket_id, category, source_type, source_id, calculation_status,
    description, quantity, unit, unit_cost, amount, incurred_at, created_by_id,
    created_at, updated_at, version
)
SELECT (md5(id::text || ':legacy-labor'))::uuid,
       ticket_id, 'LABOR', 'WORK_LOG', id, 'MISSING_RATE',
       'Nhân công: ' || LEFT(content, 470), GREATEST(minutes_spent, 1), 'PHÚT', NULL, NULL,
       created_at, technician_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM work_logs
WHERE minutes_spent > 0
ON CONFLICT (source_type, source_id) DO NOTHING;
