-- Convert the legacy V26 role into a non-login compatibility role so its
-- historical development password can never be used after this migration.
DO
$do$
DECLARE
    legacy_can_login boolean;
    legacy_is_superuser boolean;
    legacy_bypasses_rls boolean;
    migration_can_alter_role boolean;
BEGIN
    SELECT rolcanlogin, rolsuper, rolbypassrls
    INTO legacy_can_login, legacy_is_superuser, legacy_bypasses_rls
    FROM pg_roles
    WHERE rolname = 'clienthub_app';

    IF legacy_is_superuser OR legacy_bypasses_rls THEN
        RAISE EXCEPTION 'Legacy role clienthub_app must be NOSUPERUSER and NOBYPASSRLS';
    END IF;

    IF legacy_can_login THEN
        SELECT rolsuper OR rolcreaterole
        INTO migration_can_alter_role
        FROM pg_roles
        WHERE rolname = current_user;

        IF COALESCE(migration_can_alter_role, FALSE) THEN
            ALTER ROLE clienthub_app NOLOGIN PASSWORD NULL;
        ELSE
            RAISE EXCEPTION
                'Legacy role clienthub_app must be pre-provisioned as NOLOGIN';
        END IF;
    END IF;
END
$do$;

-- The hosted runtime role is normally pre-provisioned by the database platform.
-- For local/disposable databases, a privileged migration owner may create a
-- passwordless NOLOGIN role; a login password is never created by Flyway.
DO
$do$
DECLARE
    runtime_role text := '${runtimeRole}';
    migration_can_create_role boolean;
    runtime_is_superuser boolean;
    runtime_bypasses_rls boolean;
BEGIN
    IF runtime_role !~ '^[a-z_][a-z0-9_]{0,62}$' THEN
        RAISE EXCEPTION 'Invalid runtime role identifier';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = runtime_role) THEN
        SELECT rolsuper OR rolcreaterole
        INTO migration_can_create_role
        FROM pg_roles
        WHERE rolname = current_user;

        IF COALESCE(migration_can_create_role, FALSE) THEN
            EXECUTE format(
                'CREATE ROLE %I NOLOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOREPLICATION',
                runtime_role
            );
        ELSE
            RAISE EXCEPTION
                'Runtime role % must be pre-provisioned; Flyway migration user has no CREATEROLE',
                runtime_role;
        END IF;
    END IF;

    SELECT rolsuper, rolbypassrls
    INTO runtime_is_superuser, runtime_bypasses_rls
    FROM pg_roles
    WHERE rolname = runtime_role;

    IF runtime_is_superuser OR runtime_bypasses_rls THEN
        RAISE EXCEPTION 'Runtime role % must be NOSUPERUSER and NOBYPASSRLS', runtime_role;
    END IF;

    EXECUTE format('GRANT USAGE ON SCHEMA public TO %I', runtime_role);
    EXECUTE format(
        'GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO %I',
        runtime_role
    );
    EXECUTE format(
        'GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO %I',
        runtime_role
    );
    EXECUTE format(
        'ALTER DEFAULT PRIVILEGES IN SCHEMA public '
        'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I',
        runtime_role
    );
    EXECUTE format(
        'ALTER DEFAULT PRIVILEGES IN SCHEMA public '
        'GRANT USAGE, SELECT ON SEQUENCES TO %I',
        runtime_role
    );
END
$do$;
