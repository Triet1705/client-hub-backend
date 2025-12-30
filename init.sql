-- ============================================================================
-- Client Hub Database Initialization Script
-- ============================================================================
-- This script runs automatically when the PostgreSQL container starts
-- for the first time via docker-entrypoint-initdb.d
-- ============================================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";        -- UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";         -- Encryption functions
CREATE EXTENSION IF NOT EXISTS "pg_trgm";          -- Trigram matching for search

-- Create schemas for multi-module architecture
CREATE SCHEMA IF NOT EXISTS api;                   -- API layer (controllers, configs)
CREATE SCHEMA IF NOT EXISTS core;                  -- Core business logic (entities, services)
CREATE SCHEMA IF NOT EXISTS web3;                  -- Blockchain integration layer
CREATE SCHEMA IF NOT EXISTS communication;         -- Messaging and notifications

-- Set default search path for convenience
-- Application will primarily use 'core' schema
ALTER DATABASE clienthub SET search_path TO core, public;

-- Grant privileges to application user (if using non-superuser)
-- Uncomment if you create a separate application user
-- CREATE ROLE clienthub_app WITH LOGIN PASSWORD 'app_password';
-- GRANT ALL PRIVILEGES ON SCHEMA api, core, web3, communication TO clienthub_app;
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA api, core, web3, communication TO clienthub_app;
-- GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA api, core, web3, communication TO clienthub_app;

-- ============================================================================
-- Sample Initial Data (Optional - for development)
-- ============================================================================

-- Create default tenant for development
-- Uncomment when you have tenant table defined
-- INSERT INTO core.tenants (id, name, created_at) 
-- VALUES ('default', 'Default Tenant', NOW())
-- ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- Indexes for performance (add as needed)
-- ============================================================================

-- Example: Create index on tenant_id for multi-tenant queries
-- CREATE INDEX IF NOT EXISTS idx_tenant_id ON core.your_table(tenant_id);

-- ============================================================================
-- Audit logging setup (optional)
-- ============================================================================

-- Example: Create audit log table
-- CREATE TABLE IF NOT EXISTS core.audit_log (
--     id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
--     table_name TEXT NOT NULL,
--     operation TEXT NOT NULL,
--     old_data JSONB,
--     new_data JSONB,
--     changed_by TEXT,
--     changed_at TIMESTAMP DEFAULT NOW()
-- );