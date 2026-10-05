-- Organization-service owns the department record. Identity stores only its UUID.
-- Existing users with at least one operational role must belong to a department.
UPDATE users u
SET department_id = '20000000-0000-0000-0000-000000000002'
WHERE u.department_id IS NULL
  AND EXISTS (
      SELECT 1
      FROM user_roles ur
      JOIN roles r ON r.id = ur.role_id
      WHERE ur.user_id = u.id
        AND r.name <> 'ADMIN'
  );
