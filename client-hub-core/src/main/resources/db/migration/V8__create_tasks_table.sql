-- Migration: Create tasks table with soft delete and multi-tenancy support
-- Version: V1__create_tasks_table.sql

CREATE TABLE IF NOT EXISTS tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    title VARCHAR(200) NOT NULL,
    description TEXT,
    
    project_id UUID NOT NULL,
    assigned_to UUID,
    
    status VARCHAR(20) NOT NULL DEFAULT 'TODO',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    estimated_hours INTEGER,
    actual_hours INTEGER,
    due_date TIMESTAMP,
    
    tenant_id VARCHAR(255) NOT NULL,
    
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    last_modified_by VARCHAR(255),
    
    CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) 
        REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_tasks_assigned_to FOREIGN KEY (assigned_to) 
        REFERENCES users(id) ON DELETE SET NULL,
    
    CONSTRAINT chk_task_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE', 'CANCELED')),
    CONSTRAINT chk_task_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT chk_estimated_hours CHECK (estimated_hours IS NULL OR estimated_hours >= 0),
    CONSTRAINT chk_actual_hours CHECK (actual_hours IS NULL OR actual_hours >= 0)
);

CREATE INDEX idx_tasks_project_id ON tasks(project_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_tasks_assigned_to ON tasks(assigned_to) WHERE is_deleted = FALSE;
CREATE INDEX idx_tasks_tenant_id ON tasks(tenant_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_tasks_status ON tasks(status) WHERE is_deleted = FALSE;
CREATE INDEX idx_tasks_due_date ON tasks(due_date) WHERE is_deleted = FALSE;
CREATE INDEX idx_tasks_created_at ON tasks(created_at) WHERE is_deleted = FALSE;

CREATE INDEX idx_tasks_project_tenant ON tasks(project_id, tenant_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_tasks_project_status ON tasks(project_id, status) WHERE is_deleted = FALSE;
CREATE INDEX idx_tasks_assigned_tenant ON tasks(assigned_to, tenant_id) WHERE is_deleted = FALSE;

COMMENT ON TABLE tasks IS 'Task management table with soft delete and multi-tenancy support. Part of CHDEV-303 feature.';
COMMENT ON COLUMN tasks.is_deleted IS 'Soft delete flag - tasks are never physically deleted';
COMMENT ON COLUMN tasks.tenant_id IS 'Multi-tenancy isolation - enforced at application and database level';
COMMENT ON COLUMN tasks.status IS 'Task lifecycle status with state machine validation (TODO -> IN_PROGRESS -> DONE/CANCELED)';

CREATE OR REPLACE FUNCTION update_tasks_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_tasks_updated_at
    BEFORE UPDATE ON tasks
    FOR EACH ROW
    EXECUTE FUNCTION update_tasks_updated_at();
