\set ON_ERROR_STOP on

SELECT format('CREATE ROLE payment_app LOGIN PASSWORD %L', :'payment_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'payment_app')
\gexec

ALTER ROLE payment_app WITH PASSWORD :'payment_password';

SELECT 'CREATE DATABASE payment_db OWNER payment_app'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'payment_db')
\gexec

REVOKE ALL ON DATABASE payment_db FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE payment_db TO payment_app;
