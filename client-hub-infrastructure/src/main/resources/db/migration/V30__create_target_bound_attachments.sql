CREATE TABLE attachments (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    uploader_id UUID NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id VARCHAR(255) NOT NULL,
    storage_key VARCHAR(255) NOT NULL UNIQUE,
    original_filename VARCHAR(255) NOT NULL,
    media_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    last_modified_by VARCHAR(255),
    CONSTRAINT fk_attachments_uploader
        FOREIGN KEY (uploader_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_attachments_target_type
        CHECK (target_type IN ('PROJECT', 'TASK', 'INVOICE')),
    CONSTRAINT chk_attachments_size
        CHECK (size_bytes > 0 AND size_bytes <= 5242880)
);

CREATE INDEX idx_attachment_tenant_id
    ON attachments (tenant_id, id);

CREATE INDEX idx_attachment_tenant_target
    ON attachments (tenant_id, target_type, target_id);

CREATE INDEX idx_attachment_tenant_uploader
    ON attachments (tenant_id, uploader_id);

ALTER TABLE attachments ENABLE ROW LEVEL SECURITY;
ALTER TABLE attachments FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON attachments
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
