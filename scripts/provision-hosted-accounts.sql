\set ON_ERROR_STOP on

-- Run with a migration/provisioning credential, never the application runtime role.
-- Supply all psql variables through an approved secret-injection mechanism.
-- Password values must be BCrypt hashes generated outside this repository.

INSERT INTO tenants (id, name, display_name, status, created_at)
VALUES (:'tenant_id', :'tenant_id', :'tenant_display_name', 'ACTIVE', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE
SET display_name = EXCLUDED.display_name,
    status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP,
    last_modified_by = 'hosted-provisioning';

INSERT INTO roles (tenant_id, name, description, created_by)
VALUES
    (:'tenant_id', 'ADMIN', 'Full platform administrative access', 'hosted-provisioning'),
    (:'tenant_id', 'CLIENT', 'Client workspace access', 'hosted-provisioning'),
    (:'tenant_id', 'FREELANCER', 'Freelancer workspace access', 'hosted-provisioning')
ON CONFLICT (name, tenant_id) DO UPDATE
SET description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP,
    last_modified_by = 'hosted-provisioning';

INSERT INTO role_permissions (role_id, permission_id, granted_by)
SELECT r.id, p.id, 'hosted-provisioning'
FROM roles r
JOIN permissions p
  ON r.name = 'ADMIN'
  OR (r.name = 'FREELANCER' AND p.name IN ('USER_READ', 'USER_UPDATE'))
  OR (r.name = 'CLIENT' AND p.name = 'USER_READ')
WHERE r.tenant_id = :'tenant_id'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO users (tenant_id, email, password, full_name, role, is_active, created_by)
VALUES
    (:'tenant_id', :'admin_email', :'admin_password_hash', :'admin_name', 'ADMIN', TRUE, 'hosted-provisioning'),
    (:'tenant_id', :'client_email', :'client_password_hash', :'client_name', 'CLIENT', TRUE, 'hosted-provisioning'),
    (:'tenant_id', :'freelancer_email', :'freelancer_password_hash', :'freelancer_name', 'FREELANCER', TRUE, 'hosted-provisioning')
ON CONFLICT (email, tenant_id) DO UPDATE
SET password = EXCLUDED.password,
    full_name = EXCLUDED.full_name,
    role = EXCLUDED.role,
    is_active = TRUE,
    failed_login_attempts = 0,
    account_locked_until = NULL,
    updated_at = CURRENT_TIMESTAMP,
    last_modified_by = 'hosted-provisioning';

INSERT INTO user_roles (user_id, role_id, assigned_by)
SELECT u.id, r.id, 'hosted-provisioning'
FROM users u
JOIN roles r
  ON r.tenant_id = u.tenant_id
 AND r.name = u.role
WHERE u.tenant_id = :'tenant_id'
  AND u.email IN (:'admin_email', :'client_email', :'freelancer_email')
ON CONFLICT (user_id, role_id) DO NOTHING;
