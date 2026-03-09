-- V13: Enrich demo data (2026-03-08)
-- Adds 4 projects, 11 tasks, 4 invoices for a realistic demo.
-- All data scoped to tenant_id = 'default'.
-- CLIENT (owner): Jordan Lee  00000000-0000-0000-0000-000000000003
-- FREELANCER:     Alex Nguyen 00000000-0000-0000-0000-000000000002
--
-- Dashboard result: activeProjects=3, pendingTasks=10, awaitingPayment=$4,300
-- Task overview: todo=6 | inProgress=4 | done=5


-- 1. PROJECTS

INSERT INTO projects (id, tenant_id, title, description, budget, status, deadline, owner_id, created_by)
VALUES
    ('00000000-0000-0000-0002-000000000002',
     'default',
     'E-Commerce Platform Build',
     'Full-stack online storefront: product catalog, cart, checkout, and Stripe payment integration. '
         || 'Includes admin dashboard for inventory management and order tracking.',
     12000.00,
     'PLANNING',
     CURRENT_DATE + INTERVAL '75 days',
     '00000000-0000-0000-0000-000000000003',
     'system'),

    ('00000000-0000-0000-0002-000000000003',
     'default',
     'Brand Identity & Design System',
     'Complete brand refresh: logo, color palette, typography, and a reusable '
         || 'component library aligned with the new visual identity guidelines.',
     4500.00,
     'IN_PROGRESS',
     CURRENT_DATE + INTERVAL '20 days',
     '00000000-0000-0000-0000-000000000003',
     'system'),

    ('00000000-0000-0000-0002-000000000004',
     'default',
     'API Documentation Portal',
     'Developer-facing portal using Docusaurus. Covers all public REST endpoints '
         || 'with live code examples, authentication guide, and changelog.',
     2200.00,
     'ON_HOLD',
     CURRENT_DATE + INTERVAL '50 days',
     '00000000-0000-0000-0000-000000000003',
     'system'),

    ('00000000-0000-0000-0002-000000000005',
     'default',
     'Year-End Report Automation',
     'Python scripts + scheduled jobs to generate and email PDF reports from '
         || 'PostgreSQL data. Integrated with Google Sheets for non-technical stakeholders.',
     3800.00,
     'COMPLETED',
     CURRENT_DATE - INTERVAL '5 days',
     '00000000-0000-0000-0000-000000000003',
     'system')

ON CONFLICT (id) DO NOTHING;


-- 2. TASKS

INSERT INTO tasks (id, title, description, project_id, assigned_to, status, priority,
                   estimated_hours, actual_hours, tenant_id, created_by)
VALUES
    -- E-Commerce Platform
    ('00000000-0000-0000-0003-000000000004',
     'System Architecture & DB Schema',
     'Design the full entity-relationship model: products, categories, orders, '
         || 'users, inventory. Review with client before implementation begins.',
     '00000000-0000-0000-0002-000000000002',
     '00000000-0000-0000-0000-000000000002',
     'IN_PROGRESS', 'URGENT', 12, NULL, 'default', 'system'),

    ('00000000-0000-0000-0003-000000000005',
     'Product Catalog REST API',
     'Implement CRUD endpoints for products and categories with pagination, '
         || 'filtering by category/price, and full-text search support.',
     '00000000-0000-0000-0002-000000000002',
     '00000000-0000-0000-0000-000000000002',
     'TODO', 'HIGH', 20, NULL, 'default', 'system'),

    ('00000000-0000-0000-0003-000000000006',
     'Shopping Cart & Checkout Flow',
     'Session-based cart with guest + authenticated user support. '
         || 'Multi-step checkout: address, shipping method, payment confirmation.',
     '00000000-0000-0000-0002-000000000002',
     '00000000-0000-0000-0000-000000000002',
     'TODO', 'HIGH', 24, NULL, 'default', 'system'),

    ('00000000-0000-0000-0003-000000000007',
     'Stripe Payment Gateway Integration',
     'Integrate Stripe Checkout. Handle payment intent lifecycle, webhook events '
         || '(payment_succeeded, payment_failed), and refund flows.',
     '00000000-0000-0000-0002-000000000002',
     '00000000-0000-0000-0000-000000000002',
     'TODO', 'URGENT', 16, NULL, 'default', 'system'),

    -- Brand Identity & Design System
    ('00000000-0000-0000-0003-000000000008',
     'Logo Concepts & Revisions',
     'Produce 3 initial logo directions. Present to client, collect feedback, '
         || 'and iterate to final approved mark. Deliver SVG + PNG export pack.',
     '00000000-0000-0000-0002-000000000003',
     '00000000-0000-0000-0000-000000000002',
     'DONE', 'HIGH', 14, 12, 'default', 'system'),

    ('00000000-0000-0000-0003-000000000009',
     'Color System & Typography Guide',
     'Define primary/secondary/neutral palettes with accessibility-compliant '
         || 'contrast ratios. Select and license typefaces. Publish to Figma.',
     '00000000-0000-0000-0002-000000000003',
     '00000000-0000-0000-0000-000000000002',
     'IN_PROGRESS', 'HIGH', 8, NULL, 'default', 'system'),

    ('00000000-0000-0000-0003-000000000010',
     'UI Component Library Stubs',
     'Build Storybook with Button, Input, Card, Modal, Badge components '
         || 'using the finalised color system and typography.',
     '00000000-0000-0000-0002-000000000003',
     '00000000-0000-0000-0000-000000000002',
     'TODO', 'MEDIUM', 20, NULL, 'default', 'system'),

    -- API Documentation Portal
    ('00000000-0000-0000-0003-000000000011',
     'OpenAPI Schema Draft',
     'Convert existing Postman collection to OpenAPI 3.0 YAML. '
         || 'Cover all auth, projects, tasks, and invoice endpoints.',
     '00000000-0000-0000-0002-000000000004',
     '00000000-0000-0000-0000-000000000002',
     'IN_PROGRESS', 'MEDIUM', 10, NULL, 'default', 'system'),

    ('00000000-0000-0000-0003-000000000012',
     'Developer Portal Setup',
     'Bootstrap Docusaurus site. Configure sidebar, search, and versioning. '
         || 'Deploy preview to staging URL for client review.',
     '00000000-0000-0000-0002-000000000004',
     '00000000-0000-0000-0000-000000000002',
     'TODO', 'LOW', 6, NULL, 'default', 'system'),

    -- Year-End Report Automation
    ('00000000-0000-0000-0003-000000000013',
     'Data Pipeline & Query Optimisation',
     'Write parameterised SQL queries for monthly KPI aggregation. '
         || 'Add pg indexes to keep report generation under 3 seconds.',
     '00000000-0000-0000-0002-000000000005',
     '00000000-0000-0000-0000-000000000002',
     'DONE', 'HIGH', 18, 20, 'default', 'system'),

    ('00000000-0000-0000-0003-000000000014',
     'Scheduled PDF Report Runner',
     'Cron job using Python + WeasyPrint to render and email monthly PDF '
         || 'reports to configured stakeholder distribution list.',
     '00000000-0000-0000-0002-000000000005',
     '00000000-0000-0000-0000-000000000002',
     'DONE', 'MEDIUM', 10, 8, 'default', 'system')

ON CONFLICT (id) DO NOTHING;


-- 3. INVOICES
-- FIAT amounts in whole dollars (2500 = $2,500.00).

INSERT INTO invoices (tenant_id, title, amount, due_date, status,
                      project_id, client_id, freelancer_id,
                      payment_method, created_by)
VALUES
    ('default',
     'INV-002 — Mobile App Redesign: Phase 0 (Project Kickoff)',
     1500,
     CURRENT_DATE - INTERVAL '30 days',
     'PAID',
     '00000000-0000-0000-0002-000000000001',
     '00000000-0000-0000-0000-000000000003',
     '00000000-0000-0000-0000-000000000002',
     'FIAT',
     'system'),

    ('default',
     'INV-003 — Brand Identity: Phase 1 (Logo & Color System)',
     1800,
     CURRENT_DATE + INTERVAL '7 days',
     'SENT',
     '00000000-0000-0000-0002-000000000003',
     '00000000-0000-0000-0000-000000000003',
     '00000000-0000-0000-0000-000000000002',
     'FIAT',
     'system'),

    ('default',
     'INV-004 — E-Commerce Platform: Phase 1 (Architecture & Catalog API)',
     3000,
     CURRENT_DATE + INTERVAL '21 days',
     'DRAFT',
     '00000000-0000-0000-0002-000000000002',
     '00000000-0000-0000-0000-000000000003',
     '00000000-0000-0000-0000-000000000002',
     'FIAT',
     'system'),

    ('default',
     'INV-005 — API Documentation Portal: Discovery & Schema Retainer',
     800,
     CURRENT_DATE - INTERVAL '8 days',
     'OVERDUE',
     '00000000-0000-0000-0002-000000000004',
     '00000000-0000-0000-0000-000000000003',
     '00000000-0000-0000-0000-000000000002',
     'FIAT',
     'system');
