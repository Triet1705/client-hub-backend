\set ON_ERROR_STOP on

-- Run once with the provider's database-administration facility before Flyway.
-- Supply migration_role, migration_password, runtime_role and runtime_password
-- through approved secret injection.
-- This script is not executed by the application and contains no credential value.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

SELECT format(
    'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOREPLICATION',
    :'migration_role',
    :'migration_password'
)
WHERE NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = :'migration_role'
)
\gexec

SELECT format(
    'ALTER ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOREPLICATION',
    :'migration_role',
    :'migration_password'
)
\gexec

SELECT format('ALTER DATABASE %I OWNER TO %I', current_database(), :'migration_role')
\gexec

SELECT format('ALTER SCHEMA public OWNER TO %I', :'migration_role')
\gexec

SELECT
    'CREATE ROLE clienthub_app NOLOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOREPLICATION'
WHERE NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = 'clienthub_app'
)
\gexec

SELECT format(
    'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOREPLICATION',
    :'runtime_role',
    :'runtime_password'
)
WHERE NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = :'runtime_role'
)
\gexec

SELECT format(
    'ALTER ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOREPLICATION',
    :'runtime_role',
    :'runtime_password'
)
\gexec
