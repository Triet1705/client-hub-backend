CREATE TABLE IF NOT EXISTS user_profiles (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    user_id UUID NOT NULL,
    headline VARCHAR(160),
    bio TEXT,
    skills JSONB NOT NULL DEFAULT '[]'::jsonb,
    portfolio_url VARCHAR(500),
    public_profile BOOLEAN NOT NULL DEFAULT FALSE,
    show_email BOOLEAN NOT NULL DEFAULT FALSE,
    show_wallet BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    last_modified_by VARCHAR(100),
    CONSTRAINT uk_user_profiles_tenant_user UNIQUE (tenant_id, user_id),
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_profiles_tenant_user ON user_profiles(tenant_id, user_id);
CREATE INDEX IF NOT EXISTS idx_user_profiles_skills ON user_profiles USING GIN(skills);

CREATE TABLE IF NOT EXISTS user_preferences (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    user_id UUID NOT NULL,
    theme VARCHAR(20) NOT NULL DEFAULT 'dark',
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    date_format VARCHAR(30) NOT NULL DEFAULT 'DD/MM/YYYY',
    timezone VARCHAR(80) NOT NULL DEFAULT 'UTC',
    notify_comments BOOLEAN NOT NULL DEFAULT TRUE,
    notify_tasks BOOLEAN NOT NULL DEFAULT TRUE,
    notify_projects BOOLEAN NOT NULL DEFAULT TRUE,
    notify_invoices BOOLEAN NOT NULL DEFAULT TRUE,
    quiet_hours_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    quiet_hours_start VARCHAR(5),
    quiet_hours_end VARCHAR(5),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    last_modified_by VARCHAR(100),
    CONSTRAINT uk_user_preferences_tenant_user UNIQUE (tenant_id, user_id),
    CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_preferences_tenant_user ON user_preferences(tenant_id, user_id);

ALTER TABLE user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_profiles FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy ON user_profiles
FOR ALL
USING (tenant_id = current_setting('app.current_tenant', true))
WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

ALTER TABLE user_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_preferences FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy ON user_preferences
FOR ALL
USING (tenant_id = current_setting('app.current_tenant', true))
WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
