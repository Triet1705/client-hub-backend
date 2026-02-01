-- ============================================================================
-- Migration: Create refresh_tokens table
-- Date: 2026-01-13
-- Description: Refresh tokens for JWT authentication with rotation support
-- ============================================================================

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(255) NOT NULL,
    user_id UUID NOT NULL,
    
    token VARCHAR(500) NOT NULL UNIQUE,
    expire_date TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    
    replaced_by_token_id UUID,
    last_used_at TIMESTAMP,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    device_id VARCHAR(100),
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    last_modified_by VARCHAR(100),
    
    -- Constraints
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_refresh_tokens_replaced_by FOREIGN KEY (replaced_by_token_id) REFERENCES refresh_tokens(id) ON DELETE SET NULL
);

-- Indexes for performance
CREATE INDEX idx_rt_tenant_user_revoked ON refresh_tokens(tenant_id, user_id, revoked);
CREATE INDEX idx_rt_replaced_by ON refresh_tokens(replaced_by_token_id);
CREATE INDEX idx_rt_expiry ON refresh_tokens(expire_date);
CREATE INDEX idx_rt_token ON refresh_tokens(token);

COMMENT ON TABLE refresh_tokens IS 'JWT refresh tokens with rotation support';
