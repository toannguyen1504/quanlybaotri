ALTER TABLE users ADD COLUMN account_type VARCHAR(20);
ALTER TABLE users ADD COLUMN customer_id UUID;
ALTER TABLE users ADD COLUMN site_scope VARCHAR(20);

UPDATE users u
SET account_type = CASE
        WHEN EXISTS (
            SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id
            WHERE ur.user_id = u.id AND r.name IN ('ADMIN','MANAGER','TECHNICIAN')
        ) THEN 'INTERNAL'
        ELSE 'CUSTOMER'
    END,
    site_scope = CASE
        WHEN EXISTS (
            SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id
            WHERE ur.user_id = u.id AND r.name IN ('ADMIN','MANAGER','TECHNICIAN')
        ) THEN 'ALL_SITES'
        ELSE 'SELECTED_SITES'
    END;

ALTER TABLE users ALTER COLUMN account_type SET NOT NULL;
ALTER TABLE users ALTER COLUMN site_scope SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT chk_users_account_type CHECK (account_type IN ('INTERNAL','CUSTOMER'));
ALTER TABLE users ADD CONSTRAINT chk_users_site_scope CHECK (site_scope IN ('ALL_SITES','SELECTED_SITES'));
ALTER TABLE users ADD CONSTRAINT chk_users_customer_scope CHECK (
    (account_type = 'INTERNAL' AND customer_id IS NULL)
    OR account_type = 'CUSTOMER'
);

CREATE TABLE user_site_access (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    site_id UUID NOT NULL,
    PRIMARY KEY (user_id, site_id)
);
CREATE INDEX idx_users_customer ON users(customer_id) WHERE account_type = 'CUSTOMER';
CREATE INDEX idx_user_site_access_site ON user_site_access(site_id);

INSERT INTO user_site_access(user_id, site_id)
SELECT id, department_id
FROM users
WHERE account_type = 'CUSTOMER' AND department_id IS NOT NULL
ON CONFLICT DO NOTHING;

ALTER TABLE users RENAME COLUMN department_id TO legacy_site_id;
