-- ============================================================================
-- Migration: Create users table
-- Date: 2026-01-07
-- Description: Core users table with multi-tenant support
-- ============================================================================

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    role VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    wallet_address VARCHAR(255),
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    last_modified_by VARCHAR(100),
    
    -- Constraints
    CONSTRAINT uk_users_email_tenant UNIQUE (email, tenant_id),
    CONSTRAINT uk_users_wallet_address UNIQUE (wallet_address)
);

-- Indexes for performance
CREATE INDEX idx_users_tenant_id ON users(tenant_id);
CREATE INDEX idx_users_email ON users(email);

-- Comments for documentation
COMMENT ON TABLE users IS 'Core users table with multi-tenant isolation';
COMMENT ON COLUMN users.tenant_id IS 'Tenant identifier for data isolation (from TenantContext)';
COMMENT ON COLUMN users.password IS 'BCrypt hashed password';
COMMENT ON COLUMN users.is_active IS 'User account status (active/inactive)';
COMMENT ON COLUMN users.wallet_address IS 'Blockchain wallet address (unique across all tenants)';
