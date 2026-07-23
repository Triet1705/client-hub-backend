package com.clienthub.web.integration;

import com.clienthub.web.ClientHubBackendApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ClientHubBackendApplication.class)
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthRestrictedRlsIntegrationTest {

    private static final String RUNTIME_USER = "clienthub_runtime";
    private static final String RUNTIME_PASSWORD = "runtime_test_password";
    private static final String EMAIL = "shared@example.test";
    private static final String ALPHA_PASSWORD = "AlphaPass1!";
    private static final String BETA_PASSWORD = "BetaPass2!";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("test")
            .withInitScript("restricted-runtime-role.sql");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", () -> RUNTIME_USER);
        registry.add("spring.datasource.password", () -> RUNTIME_PASSWORD);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("app.rls.enabled", () -> "true");
        registry.add("rate-limit.redis.enabled", () -> "false");
        registry.add("jwt.secret", () ->
                "restricted-role-integration-test-secret-key-at-least-thirty-two-bytes");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @AfterEach
    void clearDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE refresh_tokens, users, tenants RESTART IDENTITY CASCADE");
        }
    }

    @Test
    @DisplayName("B02: restricted non-bypass role registers, logs in, and refreshes under forced RLS")
    void restrictedRuntimeRoleCompletesAuthenticationFlow() throws Exception {
        assertRestrictedRuntimeRole();
        register("tenant-alpha", ALPHA_PASSWORD);

        MvcResult login = login("tenant-alpha", ALPHA_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant_id").value("tenant-alpha"))
                .andReturn();

        Cookie refreshCookie = login.getResponse().getCookie("refresh_token");
        assertNotNull(refreshCookie, "Successful login must set the refresh-token cookie");

        mockMvc.perform(post("/api/auth/refresh-token")
                        .header("X-Tenant-ID", "tenant-alpha")
                        .cookie(refreshCookie)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant_id").value("tenant-alpha"));
    }

    @Test
    @DisplayName("B02: invalid and cross-tenant credentials do not disclose another tenant user")
    void invalidAndCrossTenantCredentialsAreRejected() throws Exception {
        register("tenant-alpha", ALPHA_PASSWORD);
        register("tenant-beta", BETA_PASSWORD);

        login("tenant-alpha", "WrongPass9!")
                .andExpect(status().isUnauthorized());
        login("tenant-beta", ALPHA_PASSWORD)
                .andExpect(status().isUnauthorized());

        login("tenant-beta", BETA_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant_id").value("tenant-beta"));
    }

    private void register(String tenantId, String password) throws Exception {
        String request = objectMapper.writeValueAsString(new RegistrationPayload(
                "Security Tester", EMAIL, password, "CLIENT"));

        mockMvc.perform(post("/api/auth/register")
                        .header("X-Tenant-ID", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions login(
            String tenantId, String password) throws Exception {
        String request = objectMapper.writeValueAsString(new LoginPayload(EMAIL, password));
        return mockMvc.perform(post("/api/auth/login")
                .header("X-Tenant-ID", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request));
    }

    private void assertRestrictedRuntimeRole() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), RUNTIME_USER, RUNTIME_PASSWORD);
             Statement statement = connection.createStatement()) {
            ResultSet roleResult = statement.executeQuery("""
                    SELECT json_build_object(
                        'superuser', rolsuper,
                        'bypassRls', rolbypassrls
                    )::text
                    FROM pg_roles
                    WHERE rolname = current_user
                    """);
            assertTrue(roleResult.next());
            JsonNode role = objectMapper.readTree(roleResult.getString(1));
            assertFalse(role.path("superuser").asBoolean());
            assertFalse(role.path("bypassRls").asBoolean());

            ResultSet forcedRls = statement.executeQuery("""
                    SELECT count(*) = 3 AND bool_and(relrowsecurity AND relforcerowsecurity)
                    FROM pg_class
                    WHERE relname IN ('users', 'refresh_tokens', 'attachments')
                    """);
            assertTrue(forcedRls.next());
            assertTrue(forcedRls.getBoolean(1));
        }
    }

    private record RegistrationPayload(String fullName, String email, String password, String role) {}

    private record LoginPayload(String email, String password) {}
}
