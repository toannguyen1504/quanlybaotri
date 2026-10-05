CREATE TABLE branches (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    address VARCHAR(500),
    contact_email VARCHAR(190),
    contact_phone VARCHAR(30),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

INSERT INTO branches (
    id, code, name, address, active, created_at, updated_at, version
) VALUES (
    '20000000-0000-0000-0000-000000000001',
    'HQ',
    'Trụ sở chính',
    NULL,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);

ALTER TABLE departments ADD COLUMN branch_id UUID;
UPDATE departments
SET branch_id = '20000000-0000-0000-0000-000000000001'
WHERE branch_id IS NULL;
ALTER TABLE departments ALTER COLUMN branch_id SET NOT NULL;
ALTER TABLE departments
    ADD CONSTRAINT fk_departments_branch
    FOREIGN KEY (branch_id) REFERENCES branches(id);
CREATE INDEX idx_departments_branch ON departments(branch_id);

INSERT INTO departments (
    id, branch_id, code, name, description, location, active,
    created_at, updated_at, version
) VALUES (
    '20000000-0000-0000-0000-000000000002',
    '20000000-0000-0000-0000-000000000001',
    'UNASSIGNED',
    'Chưa phân loại',
    'Phòng ban kỹ thuật dùng cho dữ liệu cũ chưa được phân loại',
    NULL,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
) ON CONFLICT (code) DO NOTHING;
