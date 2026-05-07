-- Fix missing reference IDs in seed notifications data

UPDATE notifications
SET reference_id = (SELECT id::varchar FROM projects WHERE title = 'Defi Dashboard' LIMIT 1)
WHERE type = 'PROJECT_COMPLETED' AND reference_type = 'PROJECT' AND reference_id IS NULL;

UPDATE notifications
SET reference_id = (SELECT id::varchar FROM tasks WHERE title = 'Smart Contract Audit' LIMIT 1)
WHERE type = 'TASK_ASSIGNED' AND reference_type = 'TASK' AND reference_id IS NULL;

UPDATE notifications
SET reference_id = (SELECT id::varchar FROM invoices WHERE title = 'Milestone 1' LIMIT 1)
WHERE type = 'INVOICE_PAID' AND reference_type = 'INVOICE' AND reference_id IS NULL;
