ALTER TABLE users
    ADD COLUMN region_scope VARCHAR(24) NOT NULL DEFAULT 'UNASSIGNED';

ALTER TABLE users
    ADD CONSTRAINT chk_users_region_scope
    CHECK (region_scope IN ('UNASSIGNED', 'ALL_REGIONS', 'SELECTED_REGIONS'));

CREATE TABLE user_region_access (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    region_id UUID NOT NULL,
    PRIMARY KEY (user_id, region_id)
);

CREATE INDEX idx_user_region_access_region ON user_region_access(region_id);
