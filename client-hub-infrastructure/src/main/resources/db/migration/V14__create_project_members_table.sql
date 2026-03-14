-- ============================================================================
-- Migration: Create project_members table
-- Date: 2026-03-13
-- Description: Explicit project membership mapping for freelancers (F6)
-- ============================================================================

CREATE TABLE IF NOT EXISTS project_members (
    project_id UUID NOT NULL,
    user_id UUID NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_project_members PRIMARY KEY (project_id, user_id),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_project_members_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_project_members_user ON project_members(user_id);
CREATE INDEX idx_project_members_tenant ON project_members(tenant_id);
CREATE INDEX idx_project_members_project_tenant ON project_members(project_id, tenant_id);

COMMENT ON TABLE project_members IS 'Explicit project memberships for freelancers within a tenant';
