-- V33's UPDATE-only policy does not provide the SELECT visibility PostgreSQL
-- needs to identify rows under FORCE RLS. Keep it checksum-stable and correct
-- the hosted operation with a transaction-scoped FOR ALL maintenance policy.
DO
$do$
BEGIN
    IF '${hostedDemo}'::boolean THEN
        EXECUTE format(
            'CREATE POLICY flyway_hosted_seed_safety_all ON users '
            'FOR ALL TO %I USING (true) WITH CHECK (true)',
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

        DROP POLICY flyway_hosted_seed_safety_all ON users;
    END IF;
END
$do$;
