CREATE TABLE communication_threads (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    topic VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    
    target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(255) NOT NULL,
    
    author_id UUID NOT NULL, 
    
    is_deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    created_by VARCHAR(255),
    last_modified_by VARCHAR(255),
    
    CONSTRAINT fk_thread_author FOREIGN KEY (author_id) REFERENCES users(id)
);

CREATE INDEX idx_thread_tenant ON communication_threads(tenant_id);
CREATE INDEX idx_thread_target ON communication_threads(target_type, target_id);

CREATE TABLE comments (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    author_id UUID NOT NULL,
    thread_id BIGINT NOT NULL,
    
    is_deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    created_by VARCHAR(255),
    last_modified_by VARCHAR(255),
    
    CONSTRAINT fk_comment_author FOREIGN KEY (author_id) REFERENCES users(id),
    CONSTRAINT fk_comment_thread FOREIGN KEY (thread_id) REFERENCES communication_threads(id)
);

CREATE INDEX idx_comment_tenant ON comments(tenant_id);
CREATE INDEX idx_comment_thread ON comments(thread_id);

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    recipient_id UUID NOT NULL,
    message VARCHAR(500) NOT NULL,
    type VARCHAR(50) NOT NULL,
    reference_id VARCHAR(255),
    reference_type VARCHAR(50),
    is_read BOOLEAN DEFAULT FALSE,
    
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    created_by VARCHAR(255),
    last_modified_by VARCHAR(255),
    
    CONSTRAINT fk_notif_recipient FOREIGN KEY (recipient_id) REFERENCES users(id)
);

CREATE INDEX idx_notif_recipient ON notifications(recipient_id, is_read);

-- Context mapping tables (Polymorphic association with referential integrity)
CREATE TABLE task_threads (
    task_id UUID NOT NULL,
    thread_id BIGINT NOT NULL,
    PRIMARY KEY (task_id, thread_id),
    CONSTRAINT fk_task_thread_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_thread_thread FOREIGN KEY (thread_id) REFERENCES communication_threads(id) ON DELETE CASCADE
);

CREATE TABLE invoice_threads (
    invoice_id BIGINT NOT NULL,
    thread_id BIGINT NOT NULL,
    PRIMARY KEY (invoice_id, thread_id),
    CONSTRAINT fk_invoice_thread_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE,
    CONSTRAINT fk_invoice_thread_thread FOREIGN KEY (thread_id) REFERENCES communication_threads(id) ON DELETE CASCADE
);

CREATE TABLE project_threads (
    project_id UUID NOT NULL,
    thread_id BIGINT NOT NULL,
    PRIMARY KEY (project_id, thread_id),
    CONSTRAINT fk_project_thread_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_project_thread_thread FOREIGN KEY (thread_id) REFERENCES communication_threads(id) ON DELETE CASCADE
);

CREATE INDEX idx_task_threads_task ON task_threads(task_id);
CREATE INDEX idx_invoice_threads_invoice ON invoice_threads(invoice_id);
CREATE INDEX idx_project_threads_project ON project_threads(project_id);