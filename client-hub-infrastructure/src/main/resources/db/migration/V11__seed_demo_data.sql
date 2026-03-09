-- ============================================================================
-- Migration: V11 - Seed Demo Data
-- Date: 2026-03-03
-- Description: Creates 3 demo accounts (Admin, Freelancer, Client) with
--              full RBAC assignments and realistic demo project/task/invoice
--              data for development and demonstration purposes.
--
-- Demo Credentials:
--   Admin      → admin@clienthub.io      / Admin@123
--   Freelancer → freelancer@demo.com     / Freelancer@123
--   Client     → client@demo.com         / Client@123
--
-- All passwords are BCrypt-hashed (strength=12, no {bcrypt} prefix).
-- All data is scoped to tenant_id = 'default'.
-- All INSERTs use ON CONFLICT DO NOTHING — safe to re-run.
-- ============================================================================


-- ============================================================================
-- 1. DEMO USERS
-- Fixed UUIDs for stable cross-references across tables.
-- ============================================================================

INSERT INTO users (id, tenant_id, email, password, full_name, role, is_active, wallet_address)
VALUES
    -- Admin: full platform control
    ('00000000-0000-0000-0000-000000000001',
     'default',
     'admin@clienthub.io',
     '$2a$12$IG4g1Ds0lQWtRUT0/rgiZuqVsmv915ZnBBuASQuly5QvMEmAzYwZG',
     'System Admin',
     'ADMIN',
     TRUE,
     NULL),

    -- Freelancer: manages tasks, creates invoices, receives payments
    ('00000000-0000-0000-0000-000000000002',
     'default',
     'freelancer@demo.com',
     '$2a$12$K56IuZixTmkuO7Z8Q3jKoOmQUNxIJL/S9MdCg8.RYcDyJn2gJ3Z5G',
     'Alex Nguyen',
     'FREELANCER',
     TRUE,
     '0xDEMO00000000000000000000000000000000ALEX'),  -- demo wallet (not a real address)

    -- Client: commissions work, approves deliverables, initiates payments
    ('00000000-0000-0000-0000-000000000003',
     'default',
     'client@demo.com',
     '$2a$12$FtsMRUw5iqUfquJG1g5p/.a5fzOWaAF3rNqdSYAPT9jx9rYRyPrbS',
     'Jordan Lee',
     'CLIENT',
     TRUE,
     NULL)

ON CONFLICT (email, tenant_id) DO NOTHING;


-- ============================================================================
-- 2. RBAC ROLES  (roles table, scoped to 'default' tenant)
-- The users.role column drives Spring Security auth.
-- These role records are for the full RBAC permission system.
-- ============================================================================

INSERT INTO roles (id, tenant_id, name, description, created_by)
VALUES
    ('00000000-0000-0000-0001-000000000001',
     'default', 'ADMIN', 'Full platform administrative access', 'system'),

    ('00000000-0000-0000-0001-000000000002',
     'default', 'FREELANCER', 'Freelancer — manage tasks, create invoices, receive payments', 'system'),

    ('00000000-0000-0000-0001-000000000003',
     'default', 'CLIENT', 'Client — commission work and manage payments', 'system')

ON CONFLICT (name, tenant_id) DO NOTHING;


-- ============================================================================
-- 3. ROLE → PERMISSION ASSIGNMENTS
-- Uses sub-selects on permission.name so no hardcoded permission UUIDs needed.
-- ============================================================================

-- ADMIN gets every permission
INSERT INTO role_permissions (role_id, permission_id, granted_by)
SELECT
    '00000000-0000-0000-0001-000000000001',
    p.id,
    'system'
FROM permissions p
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- FREELANCER gets: read users + update own profile
INSERT INTO role_permissions (role_id, permission_id, granted_by)
SELECT
    '00000000-0000-0000-0001-000000000002',
    p.id,
    'system'
FROM permissions p
WHERE p.name IN ('USER_READ', 'USER_UPDATE')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- CLIENT gets: read users only
INSERT INTO role_permissions (role_id, permission_id, granted_by)
SELECT
    '00000000-0000-0000-0001-000000000003',
    p.id,
    'system'
FROM permissions p
WHERE p.name IN ('USER_READ')
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ============================================================================
-- 4. USER → ROLE ASSIGNMENTS
-- ============================================================================

INSERT INTO user_roles (user_id, role_id, assigned_by)
VALUES
    ('00000000-0000-0000-0000-000000000001',   -- admin user
     '00000000-0000-0000-0001-000000000001',   -- ADMIN role
     'system'),

    ('00000000-0000-0000-0000-000000000002',   -- freelancer user
     '00000000-0000-0000-0001-000000000002',   -- FREELANCER role
     'system'),

    ('00000000-0000-0000-0000-000000000003',   -- client user
     '00000000-0000-0000-0001-000000000003',   -- CLIENT role
     'system')

ON CONFLICT (user_id, role_id) DO NOTHING;


-- ============================================================================
-- 5. DEMO PROJECT
-- Owned by the CLIENT (Jordan Lee) who commissions the work.
-- owner_id = the user who created the project via API (always CLIENT per @PreAuthorize).
-- ProjectService.updateProject() and deleteProject() enforce owner check — must match CLIENT.
-- ============================================================================

INSERT INTO projects (id, tenant_id, title, description, budget, status, deadline, owner_id, created_by)
VALUES
    ('00000000-0000-0000-0002-000000000001',
     'default',
     'Mobile App Redesign — ClientHub Demo',
     'Full redesign of the client-facing mobile application. '
         || 'Scope includes UX audit, wireframes, component library, and API integration.',
     5000.00,
     'IN_PROGRESS',
     CURRENT_DATE + INTERVAL '30 days',
     '00000000-0000-0000-0000-000000000003',   -- client (Jordan Lee) owns this project
     'system')

ON CONFLICT (id) DO NOTHING;


-- ============================================================================
-- 6. DEMO TASKS
-- Assigned to the freelancer under the demo project.
-- Demonstrates the TODO → IN_PROGRESS → DONE state machine.
-- ============================================================================

INSERT INTO tasks (id, title, description, project_id, assigned_to, status, priority,
                   estimated_hours, actual_hours, tenant_id, created_by)
VALUES
    ('00000000-0000-0000-0003-000000000001',
     'UX Audit & Wireframes',
     'Conduct a full UX audit of the current app. Produce lo-fi wireframes for '
         || 'all primary screens using Figma. Deliverable: approved wireframe deck.',
     '00000000-0000-0000-0002-000000000001',
     '00000000-0000-0000-0000-000000000002',
     'DONE', 'HIGH', 16, 14, 'default', 'system'),

    ('00000000-0000-0000-0003-000000000002',
     'Implement Authentication Module',
     'Build login, registration, and JWT refresh flow aligned with the new design. '
         || 'Includes password reset and email verification screens.',
     '00000000-0000-0000-0002-000000000001',
     '00000000-0000-0000-0000-000000000002',
     'IN_PROGRESS', 'HIGH', 24, NULL, 'default', 'system'),

    ('00000000-0000-0000-0003-000000000003',
     'Create API Documentation',
     'Document all public REST endpoints using OpenAPI 3.0. '
         || 'Publish to Swagger UI embedded in the project dashboard.',
     '00000000-0000-0000-0002-000000000001',
     '00000000-0000-0000-0000-000000000002',
     'TODO', 'MEDIUM', 8, NULL, 'default', 'system')

ON CONFLICT (id) DO NOTHING;


-- ============================================================================
-- 7. DEMO INVOICE
-- Phase 1 payment. Sent by freelancer to client, status SENT (awaiting payment).
-- Links client → freelancer via the project above.
-- amount: 150000000 represents 150 USDC (6 decimal places, i.e. 150 * 10^6)
--         for crypto invoicing. For FIAT demo we leave escrow fields empty.
-- ============================================================================

INSERT INTO invoices (tenant_id, title, amount, due_date, status,
                      project_id, client_id, freelancer_id,
                      payment_method, created_by)
VALUES
    ('default',
     'INV-001 — Mobile App Redesign: Phase 1 (UX & Auth)',
     250000000,                                           -- 250 USDC (6 dp) or $2500 FIAT
     CURRENT_DATE + INTERVAL '14 days',
     'SENT',
     '00000000-0000-0000-0002-000000000001',
     '00000000-0000-0000-0000-000000000003',              -- client pays
     '00000000-0000-0000-0000-000000000002',              -- freelancer receives
     'FIAT',
     'system');
-- Note: no ON CONFLICT here — invoices use BIGSERIAL, not a natural key.
-- Re-running the migration will not duplicate invoices because Flyway tracks
-- executed versions in the flyway_schema_history table.
