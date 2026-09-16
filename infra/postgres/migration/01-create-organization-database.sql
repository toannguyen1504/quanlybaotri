\set ON_ERROR_STOP on

SELECT format('CREATE ROLE organization_app LOGIN PASSWORD %L', :'organization_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'organization_app') \gexec
SELECT format('ALTER ROLE organization_app PASSWORD %L', :'organization_password') \gexec
SELECT 'CREATE DATABASE organization_db OWNER organization_app'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'organization_db') \gexec
ALTER DATABASE organization_db OWNER TO organization_app;
