ALTER TABLE equipment RENAME COLUMN department_id TO site_id;
ALTER INDEX idx_equipment_department RENAME TO idx_equipment_site;

ALTER TABLE equipment ADD COLUMN installed_at TIMESTAMPTZ;
ALTER TABLE equipment ADD COLUMN accepted_at TIMESTAMPTZ;
ALTER TABLE equipment ADD COLUMN warranty_start_at TIMESTAMPTZ;
ALTER TABLE equipment ADD COLUMN warranty_end_at TIMESTAMPTZ;
ALTER TABLE equipment ADD CONSTRAINT chk_equipment_installation_dates
    CHECK (accepted_at IS NULL OR installed_at IS NULL OR accepted_at >= installed_at);
ALTER TABLE equipment ADD CONSTRAINT chk_equipment_warranty_dates
    CHECK (
        (warranty_start_at IS NULL AND warranty_end_at IS NULL)
        OR (warranty_start_at IS NOT NULL AND warranty_end_at IS NOT NULL AND warranty_end_at >= warranty_start_at)
    );
