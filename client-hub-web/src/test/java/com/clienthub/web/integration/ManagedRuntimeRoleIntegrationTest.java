package com.clienthub.web.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.clienthub.web.ClientHubBackendApplication;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = ClientHubBackendApplication.class)
@Testcontainers
@Tag("integration")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ManagedRuntimeRoleIntegrationTest {

    private static final String MIGRATION_USER = "clienthub_migration";
    private static final String MIGRATION_PASSWORD = "migration_test_password";
    private static final String RUNTIME_USER = "clienthub_runtime";
    private static final String RUNTIME_PASSWORD = "runtime_test_password";

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("managed_role_test")
                    .withInitScript("managed-runtime-roles.sql");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", () -> RUNTIME_USER);
        registry.add("spring.datasource.password", () -> RUNTIME_PASSWORD);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", () -> MIGRATION_USER);
        registry.add("spring.flyway.password", () -> MIGRATION_PASSWORD);
        registry.add("spring.flyway.placeholders.runtimeRole", () -> RUNTIME_USER);
        registry.add("spring.flyway.placeholders.hostedDemo", () -> "true");
        registry.add("spring.cache.type", () -> "none");
        registry.add("rate-limit.redis.enabled", () -> "false");
        registry.add(
                "jwt.secret",
                () -> "managed-role-integration-test-secret-key-at-least-thirty-two-bytes");
    }

    @Test
    @DisplayName("BF-03: non-CREATEROLE migration user grants a restricted pre-provisioned runtime role")
    void managedRolePathMigratesWithoutPrivilegedRuntime() throws Exception {
        try (Connection migrationConnection =
                        DriverManager.getConnection(
                                postgres.getJdbcUrl(), MIGRATION_USER, MIGRATION_PASSWORD);
                Statement migrationStatement = migrationConnection.createStatement()) {
            ResultSet migrationCount =
                    migrationStatement.executeQuery(
                            "SELECT count(*) FROM flyway_schema_history WHERE success");
            assertTrue(migrationCount.next());
            assertEquals(35, migrationCount.getInt(1));

            ResultSet publicSeeds =
                    migrationStatement.executeQuery(
                            """
                            SELECT count(*)
                            FROM users
                            WHERE tenant_id = 'default'
                              AND email IN (
                                  'admin@clienthub.io',
                                  'freelancer@demo.com',
                                  'client@demo.com',
                                  'jane.freelancer@demo.com',
                                  'devon.freelancer@demo.com',
                                  'minh.freelancer@demo.com'
                              )
                              AND is_active
                            """);
            assertTrue(publicSeeds.next());
            assertEquals(0, publicSeeds.getInt(1));

            ResultSet migrationRole =
                    migrationStatement.executeQuery(
                            """
                            SELECT rolsuper, rolbypassrls, rolcreaterole
                            FROM pg_roles
                            WHERE rolname = current_user
                            """);
            assertTrue(migrationRole.next());
            assertFalse(migrationRole.getBoolean("rolsuper"));
            assertFalse(migrationRole.getBoolean("rolbypassrls"));
            assertFalse(migrationRole.getBoolean("rolcreaterole"));
        }

        try (Connection runtimeConnection =
                        DriverManager.getConnection(
                                postgres.getJdbcUrl(), RUNTIME_USER, RUNTIME_PASSWORD);
                Statement runtimeStatement = runtimeConnection.createStatement()) {
            ResultSet runtimeRole =
                    runtimeStatement.executeQuery(
                            """
                            SELECT rolsuper, rolbypassrls, rolcreaterole
                            FROM pg_roles
                            WHERE rolname = current_user
                            """);
            assertTrue(runtimeRole.next());
            assertFalse(runtimeRole.getBoolean("rolsuper"));
            assertFalse(runtimeRole.getBoolean("rolbypassrls"));
            assertFalse(runtimeRole.getBoolean("rolcreaterole"));

            ResultSet forcedRls =
                    runtimeStatement.executeQuery(
                            """
                            SELECT count(*) = 3 AND bool_and(relrowsecurity AND relforcerowsecurity)
                            FROM pg_class
                            WHERE relname IN ('users', 'refresh_tokens', 'attachments')
                            """);
            assertTrue(forcedRls.next());
            assertTrue(forcedRls.getBoolean(1));
        }
    }
}
