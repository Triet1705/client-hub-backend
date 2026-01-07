-- ============================================================================
-- Migration: Create users table
-- Date: 2026-01-07
-- Description: Core users table with multi-tenant support
-- ============================================================================

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(255) NOT NULL,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    last_modified_by VARCHAR(100),
    
    -- Constraints
    CONSTRAINT uk_users_email_tenant UNIQUE (email, tenant_id),
    CONSTRAINT uk_users_username_tenant UNIQUE (username, tenant_id)
);

-- Indexes for performance
CREATE INDEX idx_users_tenant_id ON users(tenant_id);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);

-- Comments for documentation
COMMENT ON TABLE users IS 'Core users table with multi-tenant isolation';
COMMENT ON COLUMN users.tenant_id IS 'Tenant identifier for data isolation (from TenantContext)';
COMMENT ON COLUMN users.password_hash IS 'BCrypt hashed password';
