\set ON_ERROR_STOP on
\connect organization_db
CREATE EXTENSION IF NOT EXISTS dblink;

TRUNCATE TABLE departments;
INSERT INTO departments(id,code,name,description,location,contact_email,contact_phone,active,created_at,updated_at,version)
SELECT * FROM dblink('dbname=identity_db',
    'SELECT id,code,name,description,location,contact_email,contact_phone,active,created_at,updated_at,version FROM public.departments ORDER BY id')
AS source(id UUID,code VARCHAR(50),name VARCHAR(150),description VARCHAR(500),location VARCHAR(255),
    contact_email VARCHAR(190),contact_phone VARCHAR(30),active BOOLEAN,created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,version BIGINT);

DO $$
DECLARE source_count BIGINT; target_count BIGINT;
BEGIN
    SELECT count INTO source_count FROM dblink('dbname=identity_db','SELECT count(*) FROM public.departments') AS c(count BIGINT);
    SELECT count(*) INTO target_count FROM departments;
    IF source_count <> target_count THEN
        RAISE EXCEPTION 'Department count mismatch: source %, target %', source_count, target_count;
    END IF;
END $$;

ALTER TABLE departments OWNER TO organization_app;
GRANT ALL PRIVILEGES ON TABLE departments TO organization_app;
