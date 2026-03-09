-- V12: Fix three data errors in V11 seed data (2026-03-08)
--   1. Demo project owner_id was freelancer — must be CLIENT (ProjectService enforces owner-only edit/delete)
--   2. FREELANCER role description was inaccurate
--   3. INV-001 amount 250000000 (crypto template copy) → 2500 (FIAT dollars)

-- 1. Fix project owner
UPDATE projects
SET owner_id = '00000000-0000-0000-0000-000000000003'
WHERE id = '00000000-0000-0000-0002-000000000001'
  AND tenant_id = 'default';

-- 2. Fix role description
UPDATE roles
SET description = 'Freelancer — manage tasks, create invoices, receive payments'
WHERE name = 'FREELANCER'
  AND tenant_id = 'default';

-- 3. Fix invoice amount
UPDATE invoices
SET amount = 2500
WHERE title LIKE 'INV-001%'
  AND tenant_id = 'default'
  AND payment_method = 'FIAT';
