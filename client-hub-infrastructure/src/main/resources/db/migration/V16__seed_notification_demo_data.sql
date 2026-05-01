-- Migration to seed demo notifications

INSERT INTO notifications (tenant_id, recipient_id, message, type, reference_id, reference_type, is_read, created_at)
SELECT 
    'default',
    id,
    'Welcome to Client Hub! Your account is ready.',
    'NEW_COMMENT',
    null,
    null,
    false,
    NOW() - INTERVAL '1 day'
FROM users WHERE email = 'client@demo.com';

INSERT INTO notifications (tenant_id, recipient_id, message, type, reference_id, reference_type, is_read, created_at)
SELECT 
    'default',
    id,
    'Project "Defi Dashboard" has been completed.',
    'PROJECT_COMPLETED',
    null,
    'PROJECT',
    false,
    NOW() - INTERVAL '2 hours'
FROM users WHERE email = 'client@demo.com';

INSERT INTO notifications (tenant_id, recipient_id, message, type, reference_id, reference_type, is_read, created_at)
SELECT 
    'default',
    id,
    'Task "Smart Contract Audit" was assigned to you.',
    'TASK_ASSIGNED',
    null,
    'TASK',
    false,
    NOW() - INTERVAL '3 hours'
FROM users WHERE email = 'freelancer@demo.com';

INSERT INTO notifications (tenant_id, recipient_id, message, type, reference_id, reference_type, is_read, created_at)
SELECT 
    'default',
    id,
    'Invoice #1001 has been marked as PAID.',
    'INVOICE_PAID',
    null,
    'INVOICE',
    false,
    NOW() - INTERVAL '1 hour'
FROM users WHERE email = 'freelancer@demo.com';

INSERT INTO notifications (tenant_id, recipient_id, message, type, reference_id, reference_type, is_read, created_at)
SELECT 
    'default',
    id,
    'System Alert: Weekly backup completed successfully.',
    'NEW_COMMENT',
    null,
    null,
    false,
    NOW() - INTERVAL '5 hours'
FROM users WHERE email = 'admin@clienthub.io';
