package com.clienthub.web.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test that proves PostgreSQL Row-Level Security (RLS)
 * enforces cross-tenant isolation at the database level.
 *
 * <p>This test uses a raw JDBC connection (not JPA) to verify that
 * RLS policies work independently of any application-layer filtering.</p>
 *
 * <p>Tagged as "integration" — excluded from default {@code mvn test} runs.
 * Run explicitly via {@code mvn test -pl client-hub-web -Pintegration-tests}.</p>
 *
 * <p><b>Requires Docker</b> (Testcontainers spins up a real PostgreSQL instance).</p>
 */
@Testcontainers
@Tag("integration")
public class RlsIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final String TENANT_A = "tenant-alpha";
    private static final String TENANT_B = "tenant-beta";
    private static final String APP_USER = "clienthub_app";
    private static final String APP_PASSWORD = "app_test_password";

    @BeforeAll
    static void setupSchema() throws SQLException {
        try (Connection superConn = getSuperuserConnection()) {
            try (Statement stmt = superConn.createStatement()) {
                // 1. Create a minimal tenant-scoped table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
                        email VARCHAR(255) NOT NULL,
                        full_name VARCHAR(255),
                        tenant_id VARCHAR(255) NOT NULL
                    )
                """);

                // 2. Enable RLS and create the tenant isolation policy
                stmt.execute("ALTER TABLE users ENABLE ROW LEVEL SECURITY");
                stmt.execute("ALTER TABLE users FORCE ROW LEVEL SECURITY");
                stmt.execute("""
                    CREATE POLICY tenant_isolation_policy ON users
                    FOR ALL
                    USING (tenant_id = current_setting('app.current_tenant', true))
                    WITH CHECK (tenant_id = current_setting('app.current_tenant', true))
                """);

                // 3. Create the restricted application user
                stmt.execute(String.format(
                    "CREATE ROLE %s LOGIN PASSWORD '%s'", APP_USER, APP_PASSWORD
                ));
                stmt.execute(String.format(
                    "GRANT CONNECT ON DATABASE %s TO %s", postgres.getDatabaseName(), APP_USER
                ));
                stmt.execute(String.format(
                    "GRANT USAGE ON SCHEMA public TO %s", APP_USER
                ));
                stmt.execute(String.format(
                    "GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO %s", APP_USER
                ));

                // 4. Seed data: two users, one per tenant
                stmt.execute(String.format(
                    "INSERT INTO users (email, full_name, tenant_id) VALUES ('alice@alpha.com', 'Alice', '%s')", TENANT_A
                ));
                stmt.execute(String.format(
                    "INSERT INTO users (email, full_name, tenant_id) VALUES ('bob@beta.com', 'Bob', '%s')", TENANT_B
                ));
            }
        }
    }

    @AfterAll
    static void cleanup() throws SQLException {
        try (Connection superConn = getSuperuserConnection()) {
            try (Statement stmt = superConn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS users CASCADE");
                stmt.execute(String.format("DROP OWNED BY %s", APP_USER));
                stmt.execute(String.format("DROP ROLE IF EXISTS %s", APP_USER));
            }
        }
    }

    @Test
    @DisplayName("Tenant A sees only its own rows, not Tenant B's")
    void tenantA_cannotSeeTenantB() throws SQLException {
        try (Connection conn = getAppUserConnection()) {
            setTenant(conn, TENANT_A);

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Tenant A should see exactly 1 row");
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT email FROM users")) {
                assertTrue(rs.next());
                assertEquals("alice@alpha.com", rs.getString(1),
                    "Tenant A should only see alice@alpha.com");
            }
        }
    }

    @Test
    @DisplayName("Tenant B sees only its own rows, not Tenant A's")
    void tenantB_cannotSeeTenantA() throws SQLException {
        try (Connection conn = getAppUserConnection()) {
            setTenant(conn, TENANT_B);

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Tenant B should see exactly 1 row");
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT email FROM users")) {
                assertTrue(rs.next());
                assertEquals("bob@beta.com", rs.getString(1),
                    "Tenant B should only see bob@beta.com");
            }
        }
    }

    @Test
    @DisplayName("No tenant set returns zero rows (not an error)")
    void noTenantSet_returnsZeroRows() throws SQLException {
        try (Connection conn = getAppUserConnection()) {
            // Deliberately do NOT call setTenant

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1),
                    "Without app.current_tenant, RLS should return 0 rows");
            }
        }
    }

    @Test
    @DisplayName("Tenant A cannot INSERT into Tenant B's namespace")
    void tenantA_cannotInsertForTenantB() throws SQLException {
        try (Connection conn = getAppUserConnection()) {
            setTenant(conn, TENANT_A);

            // Try to insert a row with tenant_id = TENANT_B while session is TENANT_A
            boolean policyViolation = false;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (email, full_name, tenant_id) VALUES (?, ?, ?)")) {
                ps.setString(1, "mallory@evil.com");
                ps.setString(2, "Mallory");
                ps.setString(3, TENANT_B);
                ps.executeUpdate();
            } catch (SQLException e) {
                // RLS WITH CHECK should block this insert
                policyViolation = e.getMessage().contains("policy")
                    || e.getMessage().contains("check")
                    || e.getSQLState().equals("42501"); // insufficient_privilege
            }

            assertTrue(policyViolation,
                "RLS should prevent Tenant A from inserting rows for Tenant B");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static Connection getSuperuserConnection() throws SQLException {
        return DriverManager.getConnection(
            postgres.getJdbcUrl(),
            postgres.getUsername(),
            postgres.getPassword()
        );
    }

    private static Connection getAppUserConnection() throws SQLException {
        return DriverManager.getConnection(
            postgres.getJdbcUrl(),
            APP_USER,
            APP_PASSWORD
        );
    }

    private static void setTenant(Connection conn, String tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT set_config('app.current_tenant', ?, false)")) {
            ps.setString(1, tenantId);
            ps.executeQuery();
        }
    }
}
