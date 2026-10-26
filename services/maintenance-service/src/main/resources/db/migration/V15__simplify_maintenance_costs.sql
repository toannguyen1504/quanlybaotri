-- Nhân công là chi phí nội bộ của công ty và không được tính vào phiếu khách hàng.
-- Lưu bản sao dữ liệu cũ để phục vụ đối soát lịch sử khi cần.
CREATE TABLE archived_ticket_labor_cost_items AS
SELECT * FROM ticket_cost_items WHERE category = 'LABOR';

CREATE TABLE archived_labor_rates AS
SELECT * FROM labor_rates;

UPDATE attachments
SET cost_item_id = NULL
WHERE cost_item_id IN (
    SELECT id FROM ticket_cost_items WHERE category = 'LABOR'
);

DELETE FROM ticket_cost_items WHERE category = 'LABOR';

-- Các nhóm nhập tay cũ được hợp nhất thành một nhóm "phát sinh".
UPDATE ticket_cost_items
SET category = 'OTHER'
WHERE category IN ('OUTSOURCED', 'TRAVEL');

-- Chi phí linh kiện kế thừa vẫn là chi phí linh kiện.
UPDATE ticket_cost_items
SET category = 'PART'
WHERE category = 'LEGACY';

UPDATE ticket_cost_summaries
SET other_total = other_total + outsourced_total + travel_total,
    labor_total = 0,
    outsourced_total = 0,
    travel_total = 0,
    grand_total = parts_total + other_total + outsourced_total + travel_total,
    updated_at = CURRENT_TIMESTAMP;

ALTER TABLE ticket_cost_items DROP CONSTRAINT IF EXISTS chk_ticket_cost_category;
ALTER TABLE ticket_cost_items
    ADD CONSTRAINT chk_ticket_cost_category CHECK(category IN ('PART', 'OTHER'));

ALTER TABLE ticket_cost_items DROP CONSTRAINT IF EXISTS chk_ticket_cost_source;
ALTER TABLE ticket_cost_items
    ADD CONSTRAINT chk_ticket_cost_source CHECK(source_type IN ('INVENTORY', 'MANUAL', 'MIGRATION'));

ALTER TABLE ticket_cost_items DROP CONSTRAINT IF EXISTS chk_ticket_cost_calculation;
ALTER TABLE ticket_cost_items
    ADD CONSTRAINT chk_ticket_cost_calculation CHECK(calculation_status = 'READY');

DROP TABLE IF EXISTS labor_rates;

ALTER TABLE ticket_cost_summaries DROP COLUMN labor_total;
ALTER TABLE ticket_cost_summaries DROP COLUMN outsourced_total;
ALTER TABLE ticket_cost_summaries DROP COLUMN travel_total;
