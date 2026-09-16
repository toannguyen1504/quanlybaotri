#!/bin/sh
set -eu

create_database() {
  database="$1"
  username="$2"
  password="$3"
  psql --set ON_ERROR_STOP=on --set=user_password="$password" --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-SQL
    CREATE USER ${username} WITH PASSWORD :'user_password';
    CREATE DATABASE ${database} OWNER ${username};
    REVOKE ALL ON DATABASE ${database} FROM PUBLIC;
    GRANT CONNECT, TEMPORARY ON DATABASE ${database} TO ${username};
SQL
}

create_database identity_db identity_app "$IDENTITY_DB_PASSWORD"
create_database organization_db organization_app "$ORGANIZATION_DB_PASSWORD"
create_database asset_db asset_app "$ASSET_DB_PASSWORD"
create_database maintenance_db maintenance_app "$MAINTENANCE_DB_PASSWORD"
create_database inventory_db inventory_app "$INVENTORY_DB_PASSWORD"
create_database notification_db notification_app "$NOTIFICATION_DB_PASSWORD"
