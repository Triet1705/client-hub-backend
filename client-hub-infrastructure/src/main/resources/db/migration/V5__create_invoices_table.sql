-- ### File: src/main/resources/db/migration/V5__create_invoices_table.sql
-- ============================================================================
-- Migration: Create invoices table (Web3 Integrated)
-- ============================================================================

CREATE TABLE IF NOT EXISTS invoices (
    id BIGSERIAL PRIMARY KEY,
    
    tenant_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    
    amount NUMERIC(50, 0) NOT NULL CHECK (amount > 0),
    
    due_date DATE NOT NULL,
    paid_at TIMESTAMP,
    
    -- Enum: DRAFT, SENT, CRYPTO_ESCROW_WAITING, DEPOSIT_DETECTED, LOCKED, PAID, REFUNDED...
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    
    project_id UUID NOT NULL,
    client_id UUID NOT NULL,
    freelancer_id UUID NOT NULL,
    
    payment_method VARCHAR(50) NOT NULL DEFAULT 'FIAT',
    wallet_address VARCHAR(255), 
    tx_hash VARCHAR(255),        
    smart_contract_id VARCHAR(255), 
    escrow_status VARCHAR(50) DEFAULT 'NOT_STARTED',
    confirmations INTEGER DEFAULT 0,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    last_modified_by VARCHAR(100),

    CONSTRAINT fk_invoices_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_invoices_client FOREIGN KEY (client_id) REFERENCES users(id),
    CONSTRAINT fk_invoices_freelancer FOREIGN KEY (freelancer_id) REFERENCES users(id)
);

CREATE INDEX idx_invoices_tenant_id ON invoices(tenant_id);
CREATE INDEX idx_invoices_status ON invoices(status);
CREATE INDEX idx_invoices_project ON invoices(project_id);
CREATE INDEX idx_invoices_client ON invoices(client_id);
CREATE INDEX idx_invoices_wallet ON invoices(wallet_address);