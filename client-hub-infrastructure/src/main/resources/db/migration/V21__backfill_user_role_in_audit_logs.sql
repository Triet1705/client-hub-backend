UPDATE audit_logs 
SET user_role = (SELECT role FROM users WHERE users.id = audit_logs.user_id) 
WHERE user_role IS NULL AND user_id IS NOT NULL;
