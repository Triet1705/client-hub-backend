-- ============================================================================
-- Migration: V15 - Seed additional freelancer demo data
-- Date: 2026-03-13
-- Description: Adds 3 freelancer demo accounts and richer member/task/invoice
--              data to make project detail + F6 membership demos more realistic.
--
-- New demo freelancer credentials (same password hash as Freelancer@123):
--   jane.freelancer@demo.com
--   devon.freelancer@demo.com
--   minh.freelancer@demo.com
-- ============================================================================

-- 1) Additional freelancer users
INSERT INTO users (id, tenant_id, email, password, full_name, role, is_active, wallet_address)
VALUES
    ('00000000-0000-0000-0000-000000000004',
     'default',
     'jane.freelancer@demo.com',
     '$2a$12$K56IuZixTmkuO7Z8Q3jKoOmQUNxIJL/S9MdCg8.RYcDyJn2gJ3Z5G',
     'Jane Park',
     'FREELANCER',
     TRUE,
     '0xDEMO00000000000000000000000000000000JANE'),

    ('00000000-0000-0000-0000-000000000005',
     'default',
     'devon.freelancer@demo.com',
     '$2a$12$K56IuZixTmkuO7Z8Q3jKoOmQUNxIJL/S9MdCg8.RYcDyJn2gJ3Z5G',
     'Devon Tran',
     'FREELANCER',
     TRUE,
     '0xDEMO00000000000000000000000000000000DEVN'),

    ('00000000-0000-0000-0000-000000000006',
     'default',
     'minh.freelancer@demo.com',
     '$2a$12$K56IuZixTmkuO7Z8Q3jKoOmQUNxIJL/S9MdCg8.RYcDyJn2gJ3Z5G',
     'Minh Vo',
     'FREELANCER',
     TRUE,
     '0xDEMO00000000000000000000000000000000MINH')
ON CONFLICT (email, tenant_id) DO NOTHING;


-- 2) Ensure user_roles mapping for new freelancers
INSERT INTO user_roles (user_id, role_id, assigned_by)
SELECT u.id, r.id, 'system'
FROM users u
JOIN roles r ON r.name = 'FREELANCER' AND r.tenant_id = u.tenant_id
WHERE u.tenant_id = 'default'
  AND u.id IN (
      '00000000-0000-0000-0000-000000000004',
      '00000000-0000-0000-0000-000000000005',
      '00000000-0000-0000-0000-000000000006'
  )
ON CONFLICT (user_id, role_id) DO NOTHING;


-- 3) Seed explicit project memberships (F6 model)
-- Existing freelancer (..0002) is added as member across active projects to keep demo continuity.
INSERT INTO project_members (project_id, user_id, tenant_id)
VALUES
    ('00000000-0000-0000-0002-000000000001', '00000000-0000-0000-0000-000000000002', 'default'),
    ('00000000-0000-0000-0002-000000000002', '00000000-0000-0000-0000-000000000002', 'default'),
    ('00000000-0000-0000-0002-000000000003', '00000000-0000-0000-0000-000000000002', 'default'),
    ('00000000-0000-0000-0002-000000000004', '00000000-0000-0000-0000-000000000002', 'default'),

    ('00000000-0000-0000-0002-000000000002', '00000000-0000-0000-0000-000000000004', 'default'),
    ('00000000-0000-0000-0002-000000000003', '00000000-0000-0000-0000-000000000004', 'default'),

    ('00000000-0000-0000-0002-000000000001', '00000000-0000-0000-0000-000000000005', 'default'),
    ('00000000-0000-0000-0002-000000000004', '00000000-0000-0000-0000-000000000005', 'default'),

    ('00000000-0000-0000-0002-000000000003', '00000000-0000-0000-0000-000000000006', 'default')
ON CONFLICT (project_id, user_id) DO NOTHING;


-- 4) Additional tasks across freelancers
INSERT INTO tasks (id, title, description, project_id, assigned_to, status, priority,
                   estimated_hours, actual_hours, due_date, tenant_id, created_by)
VALUES
    ('00000000-0000-0000-0003-000000000015',
     'Design checkout error states',
     'Create edge-case UI states for failed payments, timeout retries, and cart restoration paths.',
     '00000000-0000-0000-0002-000000000002',
     '00000000-0000-0000-0000-000000000004',
     'IN_PROGRESS', 'MEDIUM', 10, 4, CURRENT_TIMESTAMP + INTERVAL '5 days', 'default', 'system'),

    ('00000000-0000-0000-0003-000000000016',
     'Accessibility QA pass (WCAG 2.1)',
     'Run keyboard and contrast audits on key project screens and document remediation checklist.',
     '00000000-0000-0000-0002-000000000003',
     '00000000-0000-0000-0000-000000000004',
     'TODO', 'HIGH', 12, NULL, CURRENT_TIMESTAMP + INTERVAL '9 days', 'default', 'system'),

    ('00000000-0000-0000-0003-000000000017',
     'Thread-level caching for comments',
     'Add Redis-backed cache for communication thread retrieval with tenant-safe invalidation.',
     '00000000-0000-0000-0002-000000000001',
     '00000000-0000-0000-0000-000000000005',
     'TODO', 'URGENT', 14, NULL, CURRENT_TIMESTAMP + INTERVAL '6 days', 'default', 'system'),

    ('00000000-0000-0000-0003-000000000018',
     'Export API docs to static bundle',
     'Generate static distribution artifact from docs portal and publish to staging bucket.',
     '00000000-0000-0000-0002-000000000004',
     '00000000-0000-0000-0000-000000000005',
     'DONE', 'LOW', 6, 6, CURRENT_TIMESTAMP - INTERVAL '2 days', 'default', 'system'),

    ('00000000-0000-0000-0003-000000000019',
     'Component token cleanup',
     'Normalize spacing and typography tokens in the shared design system package.',
     '00000000-0000-0000-0002-000000000003',
     '00000000-0000-0000-0000-000000000006',
     'IN_PROGRESS', 'MEDIUM', 8, 3, CURRENT_TIMESTAMP + INTERVAL '4 days', 'default', 'system')
ON CONFLICT (id) DO NOTHING;


-- 5) Additional invoices (FIAT whole-dollar convention)
INSERT INTO invoices (tenant_id, title, amount, due_date, status,
                      project_id, client_id, freelancer_id,
                      payment_method, created_by)
VALUES
    ('default',
     'INV-006 — E-Commerce UX & QA sprint',
     1100,
     CURRENT_DATE + INTERVAL '10 days',
     'SENT',
     '00000000-0000-0000-0002-000000000002',
     '00000000-0000-0000-0000-000000000003',
     '00000000-0000-0000-0000-000000000004',
     'FIAT',
     'system'),

    ('default',
     'INV-007 — Communication caching implementation',
     900,
     CURRENT_DATE + INTERVAL '14 days',
     'DRAFT',
     '00000000-0000-0000-0002-000000000001',
     '00000000-0000-0000-0000-000000000003',
     '00000000-0000-0000-0000-000000000005',
     'FIAT',
     'system'),

    ('default',
     'INV-008 — Design system token maintenance',
     650,
     CURRENT_DATE - INTERVAL '3 days',
     'OVERDUE',
     '00000000-0000-0000-0002-000000000003',
     '00000000-0000-0000-0000-000000000003',
     '00000000-0000-0000-0000-000000000006',
     'FIAT',
     'system');
