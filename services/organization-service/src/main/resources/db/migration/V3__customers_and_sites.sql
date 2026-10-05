ALTER TABLE branches RENAME TO customers;
ALTER TABLE customers RENAME CONSTRAINT branches_pkey TO customers_pkey;
ALTER TABLE customers RENAME CONSTRAINT branches_code_key TO customers_code_key;

ALTER TABLE departments RENAME TO customer_sites;
ALTER TABLE customer_sites RENAME COLUMN branch_id TO customer_id;
ALTER TABLE customer_sites RENAME CONSTRAINT departments_pkey TO customer_sites_pkey;
ALTER TABLE customer_sites RENAME CONSTRAINT departments_code_key TO customer_sites_code_key;
ALTER TABLE customer_sites RENAME CONSTRAINT fk_departments_branch TO fk_customer_sites_customer;
ALTER INDEX idx_departments_branch RENAME TO idx_customer_sites_customer;

UPDATE customers SET name = 'Khách hàng chưa phân loại' WHERE code = 'HQ' AND name = 'Trụ sở chính';
UPDATE customer_sites SET name = 'Địa điểm chưa phân loại', description = 'Địa điểm dùng cho dữ liệu cũ chưa được phân loại'
WHERE code = 'UNASSIGNED';
