UPDATE equipment
SET department_id = '20000000-0000-0000-0000-000000000002'
WHERE department_id IS NULL;
ALTER TABLE equipment ALTER COLUMN department_id SET NOT NULL;
