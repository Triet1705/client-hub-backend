ALTER TABLE audit_anchor_batches ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_audit_anchor_batches_retry
    ON audit_anchor_batches(status, next_attempt_at);
