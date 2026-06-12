-- Phase 4: Create certificates table for SBTs
CREATE TABLE IF NOT EXISTS certificates (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    user_id UUID NOT NULL,
    project_id UUID NOT NULL,
    token_id VARCHAR(255) NOT NULL,
    metadata_uri VARCHAR(1000) NOT NULL,
    transaction_hash VARCHAR(255) NOT NULL,
    minted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_cert_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_cert_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT uq_cert_project_user UNIQUE (project_id, user_id)
);

CREATE INDEX idx_cert_user ON certificates(user_id);
CREATE INDEX idx_cert_tenant ON certificates(tenant_id);
