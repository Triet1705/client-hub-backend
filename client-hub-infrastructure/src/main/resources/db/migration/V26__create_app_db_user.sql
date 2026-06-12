-- ============================================================================
-- Migration: Create restricted DB user for the application
-- ============================================================================

DO
$do$
BEGIN
   IF NOT EXISTS (
      SELECT FROM pg_catalog.pg_roles
      WHERE  rolname = 'clienthub_app') THEN

      CREATE ROLE clienthub_app WITH LOGIN PASSWORD 'clienthub_app_password';
   END IF;
END
$do$;

-- Grant basic privileges to the app user on the current schema
GRANT USAGE ON SCHEMA public TO clienthub_app;

-- Grant SELECT, INSERT, UPDATE, DELETE on all current and future tables in the public schema
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO clienthub_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO clienthub_app;

-- Grant usage on all sequences (needed for auto-incrementing IDs)
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO clienthub_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO clienthub_app;
