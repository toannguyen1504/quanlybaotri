ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE users ADD COLUMN google_subject VARCHAR(255);
ALTER TABLE users ADD COLUMN registration_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE users ADD COLUMN registration_source VARCHAR(20) NOT NULL DEFAULT 'ADMIN';
ALTER TABLE users ADD COLUMN requested_organization_name VARCHAR(190);
ALTER TABLE users ADD COLUMN registration_note VARCHAR(1000);
ALTER TABLE users ADD COLUMN rejection_reason VARCHAR(1000);
ALTER TABLE users ADD COLUMN registered_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN reviewed_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN reviewed_by_id UUID;

UPDATE users SET registered_at = created_at WHERE registered_at IS NULL;

ALTER TABLE users ADD CONSTRAINT chk_users_registration_status
    CHECK (registration_status IN ('PENDING', 'APPROVED', 'REJECTED'));
ALTER TABLE users ADD CONSTRAINT chk_users_registration_source
    CHECK (registration_source IN ('ADMIN', 'LOCAL', 'GOOGLE'));

CREATE UNIQUE INDEX uq_users_google_subject
    ON users(google_subject) WHERE google_subject IS NOT NULL;
CREATE INDEX idx_users_registration_status
    ON users(registration_status, registered_at DESC);
