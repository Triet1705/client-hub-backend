-- ============================================================================
-- Migration: Create tenants table
-- Date: 2026-01-07
-- Description: Tenants registry for multi-tenant architecture
-- ============================================================================

CREATE TABLE IF NOT EXISTS tenants (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    
    -- Configuration
    settings JSONB,
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    last_modified_by VARCHAR(100),
    
    -- Constraints
    CONSTRAINT uk_tenants_name UNIQUE (name),
    CONSTRAINT chk_tenant_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'INACTIVE'))
);

-- Indexes
CREATE INDEX idx_tenants_status ON tenants(status);
CREATE INDEX idx_tenants_settings ON tenants USING GIN(settings);

-- Default tenant for development
INSERT INTO tenants (id, name, display_name, status, created_at) 
VALUES ('default', 'default', 'Default Tenant', 'ACTIVE', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Comments
COMMENT ON TABLE tenants IS 'Multi-tenant registry';
COMMENT ON COLUMN tenants.settings IS 'Tenant-specific configuration in JSON format';
