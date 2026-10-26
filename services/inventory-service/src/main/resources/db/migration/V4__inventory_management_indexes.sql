CREATE INDEX IF NOT EXISTS idx_parts_active_code ON parts (active, code);
CREATE INDEX IF NOT EXISTS idx_parts_stock ON parts (current_stock, minimum_stock);
CREATE INDEX IF NOT EXISTS idx_stock_movements_created_type ON stock_movements (created_at DESC, movement_type);
CREATE INDEX IF NOT EXISTS idx_stock_movements_part_created ON stock_movements (part_id, created_at DESC);
