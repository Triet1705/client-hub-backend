package com.clienthub.web.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.clienthub.web.ClientHubBackendApplication;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
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
class HostedSeedSafetyIntegrationTest {

    private static final String[] PUBLIC_PASSWORDS = {
        "Admin@123", "Freelancer@123", "Client@123"
    };

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine").withDatabaseName("hosted_seed_safety");

    @Autowired private PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.placeholders.hostedDemo", () -> "true");
        registry.add("spring.cache.type", () -> "none");
        registry.add("rate-limit.redis.enabled", () -> "false");
        registry.add(
                "jwt.secret",
                () -> "hosted-seed-safety-test-secret-key-at-least-thirty-two-bytes");
    }

    @Test
    @DisplayName("BF-01: hosted migrations deactivate and re-hash every public demo account")
    void publicDemoAccountsAreDisabledAndPasswordsInvalidated() throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                postgres.getJdbcUrl(),
                                postgres.getUsername(),
                                postgres.getPassword());
                Statement statement = connection.createStatement();
                ResultSet users =
                        statement.executeQuery(
                                """
                                SELECT email, password, is_active
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
                                ORDER BY email
                                """)) {
            int count = 0;
            while (users.next()) {
                count++;
                assertFalse(users.getBoolean("is_active"));
                String storedHash = users.getString("password");
                for (String publicPassword : PUBLIC_PASSWORDS) {
                    assertFalse(passwordEncoder.matches(publicPassword, storedHash));
                }
            }
            assertEquals(6, count);
        }
    }
}
