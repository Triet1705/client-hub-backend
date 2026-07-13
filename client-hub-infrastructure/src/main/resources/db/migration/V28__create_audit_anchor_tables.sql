CREATE TABLE audit_anchor_batches (
    id UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    merkle_root VARCHAR(66) NOT NULL UNIQUE,
    metadata_hash VARCHAR(66) NOT NULL,
    hash_version VARCHAR(50) NOT NULL,
    chain_id BIGINT,
    contract_address VARCHAR(42),
    first_log_id BIGINT NOT NULL,
    last_log_id BIGINT NOT NULL,
    record_count INTEGER NOT NULL,
    transaction_hash VARCHAR(66),
    submitted_block NUMERIC(78, 0),
    confirmed_block NUMERIC(78, 0),
    confirmations INTEGER NOT NULL DEFAULT 0,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE,
    confirmed_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE audit_anchor_members (
    id BIGSERIAL PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES audit_anchor_batches(id) ON DELETE CASCADE,
    audit_log_id BIGINT NOT NULL UNIQUE REFERENCES audit_logs(id),
    leaf_index INTEGER NOT NULL,
    leaf_hash VARCHAR(66) NOT NULL,
    merkle_proof TEXT NOT NULL,
    CONSTRAINT uq_audit_anchor_batch_index UNIQUE (batch_id, leaf_index)
);

CREATE INDEX idx_audit_anchor_batches_status ON audit_anchor_batches(status);
CREATE INDEX idx_audit_anchor_members_batch ON audit_anchor_members(batch_id);
