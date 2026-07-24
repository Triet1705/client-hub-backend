package com.clienthub.web.integration;

import com.clienthub.common.context.TenantContext;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.Role;
import com.clienthub.domain.repository.UserRepository;
import com.clienthub.infrastructure.security.CustomUserDetails;
import com.clienthub.web.ClientHubBackendApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ClientHubBackendApplication.class)
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminUserTenantIsolationPostgresIntegrationTest {

    private static final String TENANT_A = "admin-isolation-tenant-a";
    private static final String TENANT_B = "admin-isolation-tenant-b";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User administratorA;
    private User sameTenantTarget;
    private User clientA;
    private User freelancerA;
    private User foreignTarget;

    @BeforeEach
    void setUp() {
        cleanFixtureData();

        TenantContext.setTenantId(TENANT_A);
        administratorA = createUser(
                TENANT_A, "admin.a@admin-isolation.test", "Administrator A", Role.ADMIN);
        sameTenantTarget = createUser(
                TENANT_A, "target.a@admin-isolation.test", "Same Tenant Target", Role.CLIENT);
        clientA = createUser(
                TENANT_A, "client.a@admin-isolation.test", "Client A", Role.CLIENT);
        freelancerA = createUser(
                TENANT_A, "freelancer.a@admin-isolation.test", "Freelancer A", Role.FREELANCER);

        TenantContext.setTenantId(TENANT_B);
        foreignTarget = createUser(
                TENANT_B, "target.b@admin-isolation.test", "Foreign Tenant Target", Role.CLIENT);

        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        cleanFixtureData();
    }

    @Test
    @DisplayName("DEFECT-S3B-01: cross-tenant user detail is a generic non-disclosing 404")
    void crossTenantUserDetail_ShouldBeNonDisclosingAndMatchMissingTarget() throws Exception {
        performAs(administratorA, TENANT_A, get("/api/admin/users/{id}", sameTenantTarget.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sameTenantTarget.getId().toString()))
                .andExpect(jsonPath("$.email").value("target.a@admin-isolation.test"))
                .andExpect(jsonPath("$.tenantId").value(TENANT_A))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.failedLoginAttempts").doesNotExist())
                .andExpect(jsonPath("$.accountLockedUntil").doesNotExist())
                .andExpect(jsonPath("$.createdBy").doesNotExist());

        performAs(administratorA, TENANT_A, get("/api/admin/users/{id}", foreignTarget.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("User not found"))
                .andExpect(jsonPath("$.status_code").value(404))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString(
                        "target.b@admin-isolation.test"))))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString(TENANT_B))));

        performAs(administratorA, TENANT_A, get("/api/admin/users/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("User not found"))
                .andExpect(jsonPath("$.status_code").value(404));
    }

    @Test
    @DisplayName("Administrator user list remains explicitly tenant scoped and paginated")
    void administratorUserList_ShouldRemainTenantScoped() throws Exception {
        performAs(
                        administratorA,
                        TENANT_A,
                        get("/api/admin/users")
                                .param("page", "0")
                                .param("size", "20")
                                .param("sortBy", "email")
                                .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(4)))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(
                        foreignTarget.getId().toString()))))
                .andExpect(jsonPath("$.content[*].tenantId", not(hasItem(TENANT_B))))
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    @DisplayName("Client, Freelancer and unauthenticated actors cannot resolve Administrator targets")
    void nonAdministrators_ShouldBeDeniedBeforeTargetResolution() throws Exception {
        performAs(clientA, TENANT_A, get("/api/admin/users/{id}", foreignTarget.getId()))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString(
                        "target.b@admin-isolation.test"))));

        performAs(freelancerA, TENANT_A, get("/api/admin/users/{id}", foreignTarget.getId()))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString(
                        "target.b@admin-isolation.test"))));

        mockMvc.perform(get("/api/admin/users/{id}", foreignTarget.getId())
                        .header("X-Tenant-ID", TENANT_A))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString(
                        "target.b@admin-isolation.test"))));
    }

    @Test
    @DisplayName("Status mutation succeeds locally and cannot mutate cross-tenant or missing users")
    void statusMutation_ShouldBeTenantScopedAndLeaveForeignRecordUnchanged() throws Exception {
        performAs(
                        administratorA,
                        TENANT_A,
                        patch("/api/admin/users/{id}/status", sameTenantTarget.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"active\":false}"))
                .andExpect(status().isNoContent());

        assertEquals(false, findUser(TENANT_A, sameTenantTarget.getId()).isActive());

        performAs(
                        administratorA,
                        TENANT_A,
                        patch("/api/admin/users/{id}/status", foreignTarget.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"active\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));

        assertTrue(findUser(TENANT_B, foreignTarget.getId()).isActive());

        performAs(
                        administratorA,
                        TENANT_A,
                        patch("/api/admin/users/{id}/status", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"active\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    @DisplayName("Role change and impersonation resolve only same-tenant targets")
    void roleAndImpersonationOperations_ShouldUseTenantQualifiedTargets() throws Exception {
        performAs(
                        administratorA,
                        TENANT_A,
                        patch("/api/admin/users/{id}/role", sameTenantTarget.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"FREELANCER\"}"))
                .andExpect(status().isNoContent());
        assertEquals(Role.FREELANCER, findUser(TENANT_A, sameTenantTarget.getId()).getRole());

        performAs(
                        administratorA,
                        TENANT_A,
                        patch("/api/admin/users/{id}/role", foreignTarget.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"FREELANCER\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
        assertEquals(Role.CLIENT, findUser(TENANT_B, foreignTarget.getId()).getRole());

        performAs(
                        administratorA,
                        TENANT_A,
                        patch("/api/admin/users/{id}/role", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"FREELANCER\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));

        performAs(
                        administratorA,
                        TENANT_A,
                        post("/api/admin/impersonate/{id}", sameTenantTarget.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sameTenantTarget.getId().toString()))
                .andExpect(jsonPath("$.tenantId").value(TENANT_A))
                .andExpect(jsonPath("$.accessToken").isString());

        performAs(
                        administratorA,
                        TENANT_A,
                        post("/api/admin/impersonate/{id}", foreignTarget.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString(TENANT_B))));

        performAs(
                        administratorA,
                        TENANT_A,
                        post("/api/admin/impersonate/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    private org.springframework.test.web.servlet.ResultActions performAs(
            User actor,
            String tenantId,
            MockHttpServletRequestBuilder request
    ) throws Exception {
        CustomUserDetails userDetails = CustomUserDetails.build(actor);
        var auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        return mockMvc.perform(request
                .header("X-Tenant-ID", tenantId)
                .with(authentication(auth)));
    }

    private User createUser(String tenantId, String email, String fullName, Role role) {
        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(email);
        user.setPassword("hashed-password");
        user.setFullName(fullName);
        user.setRole(role);
        user.setActive(true);
        return userRepository.saveAndFlush(user);
    }

    private User findUser(String tenantId, UUID userId) {
        TenantContext.setTenantId(tenantId);
        try {
            return userRepository.findByIdAndTenantId(userId, tenantId).orElseThrow();
        } finally {
            TenantContext.clear();
        }
    }

    private void cleanFixtureData() {
        jdbcTemplate.update(
                "DELETE FROM refresh_tokens WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
        jdbcTemplate.update(
                "DELETE FROM users WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
    }
}
