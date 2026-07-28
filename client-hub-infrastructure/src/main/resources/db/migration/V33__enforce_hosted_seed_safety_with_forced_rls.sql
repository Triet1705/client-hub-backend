-- V31 is intentionally preserved for Flyway checksum compatibility.
-- On managed databases the non-BYPASSRLS migration owner is also subject to
-- FORCE ROW LEVEL SECURITY, so use a transaction-scoped maintenance policy.
DO
$do$
BEGIN
    IF '${hostedDemo}'::boolean THEN
        EXECUTE format(
            'CREATE POLICY flyway_hosted_seed_safety ON users '
            'FOR UPDATE TO %I USING (true) WITH CHECK (true)',
            current_user
        );

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

        DROP POLICY flyway_hosted_seed_safety ON users;
    END IF;
END
$do$;
