-- Disable all historically public demo credentials when the hosted-demo gate is enabled.
-- The default remains false so local development/demo behaviour is preserved.
DO
$do$
BEGIN
    IF '${hostedDemo}'::boolean THEN
        UPDATE users
        SET is_active = FALSE,
            password = crypt(gen_random_uuid()::text, gen_salt('bf', 12)),
            failed_login_attempts = 0,
            account_locked_until = NULL,
            updated_at = CURRENT_TIMESTAMP,
            last_modified_by = 'flyway-hosted-seed-safety'
        WHERE tenant_id = 'default'
          AND email IN (
              'admin@clienthub.io',
              'freelancer@demo.com',
              'client@demo.com',
              'jane.freelancer@demo.com',
              'devon.freelancer@demo.com',
              'minh.freelancer@demo.com'
          );
    END IF;
END
$do$;
