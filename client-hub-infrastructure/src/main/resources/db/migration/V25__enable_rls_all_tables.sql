-- ============================================================================
-- Migration: Enable Row Level Security (RLS) for all tenant-scoped tables
-- ============================================================================

DO $$ 
DECLARE 
    t_name text;
    tables text[] := ARRAY[
        'users', 'roles', 'projects', 'invoices', 'refresh_tokens',
        'tasks', 'audit_logs', 'communication_threads', 'comments',
        'notifications', 'project_members', 'certificates'
    ];
BEGIN
    FOREACH t_name IN ARRAY tables
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY;', t_name);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY;', t_name);
        
        -- Create policy for each table
        -- We use current_setting('app.current_tenant', true) so it returns NULL instead of error if not set
        EXECUTE format('
            CREATE POLICY tenant_isolation_policy ON %I 
            FOR ALL
            USING (tenant_id = current_setting(''app.current_tenant'', true))
            WITH CHECK (tenant_id = current_setting(''app.current_tenant'', true));
        ', t_name);
    END LOOP;
END $$;
