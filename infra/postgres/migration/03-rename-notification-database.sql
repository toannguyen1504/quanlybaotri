\set ON_ERROR_STOP on

SELECT pg_terminate_backend(pid) FROM pg_stat_activity
WHERE datname IN ('query_db','notification_db') AND pid <> pg_backend_pid();

SELECT 'ALTER ROLE query_app RENAME TO notification_app'
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname='query_app')
  AND NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='notification_app') \gexec
SELECT format('ALTER ROLE notification_app LOGIN PASSWORD %L', :'notification_password') \gexec

SELECT 'ALTER DATABASE query_db RENAME TO notification_db'
WHERE EXISTS (SELECT 1 FROM pg_database WHERE datname='query_db')
  AND NOT EXISTS (SELECT 1 FROM pg_database WHERE datname='notification_db') \gexec
ALTER DATABASE notification_db OWNER TO notification_app;
