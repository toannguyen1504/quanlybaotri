CREATE INDEX IF NOT EXISTS idx_equipment_status_active ON equipment (status, active);
CREATE INDEX IF NOT EXISTS idx_equipment_warranty_end ON equipment (warranty_end_at);
CREATE INDEX IF NOT EXISTS idx_equipment_site_category ON equipment (site_id, category_id);
