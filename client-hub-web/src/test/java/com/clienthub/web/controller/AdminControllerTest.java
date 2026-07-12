package com.clienthub.web.controller;

import com.clienthub.web.ClientHubBackendApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {ClientHubBackendApplication.class})
@AutoConfigureMockMvc
@ActiveProfiles("h2test")
@DisplayName("AdminController RBAC Security Tests")
public class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ========== /api/admin/analytics Tests ==========

    @Test
    @DisplayName("Analytics - Admin role should return 200 OK")
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void testGetDashboard_WithAdminRole_ShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/admin/analytics"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").isNumber())
                .andExpect(jsonPath("$.totalProjects").isNumber())
                .andExpect(jsonPath("$.totalInvoices").isNumber())
                .andExpect(jsonPath("$.systemHealth").isString());
    }

    @Test
    @DisplayName("Analytics - Client role should return 403 Forbidden")
    @WithMockUser(username = "client@test.com", roles = "CLIENT")
    void testGetDashboard_WithClientRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/analytics"))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Analytics - Freelancer role should return 403 Forbidden")
    @WithMockUser(username = "freelancer@test.com", roles = "FREELANCER")
    void testGetDashboard_WithFreelancerRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/analytics"))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Analytics - Unauthenticated user should return 401 Unauthorized")
    void testGetDashboard_WithoutAuthentication_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/analytics"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    // ========== /api/admin/health Tests ==========

    @Test
    @DisplayName("Health - Admin role should return 200 OK")
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void testGetSystemStatus_WithAdminRole_ShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/admin/health"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallStatus").isString())
                .andExpect(jsonPath("$.database").exists())
                .andExpect(jsonPath("$.redis").exists());
    }

    @Test
    @DisplayName("Health - Client role should return 403 Forbidden")
    @WithMockUser(username = "client@test.com", roles = "CLIENT")
    void testGetSystemStatus_WithClientRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/health"))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Health - Freelancer role should return 403 Forbidden")
    @WithMockUser(username = "freelancer@test.com", roles = "FREELANCER")
    void testGetSystemStatus_WithFreelancerRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/health"))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Health - Unauthenticated user should return 401 Unauthorized")
    void testGetSystemStatus_WithoutAuthentication_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/health"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    // ========== /api/admin/control-center Tests ==========

    @Test
    @DisplayName("Control Center - Admin role should return overview aggregate")
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void testGetControlCenter_WithAdminRole_ShouldReturnAggregate() throws Exception {
        mockMvc.perform(get("/api/admin/control-center"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.systemStatus").isString())
                .andExpect(jsonPath("$.health.overallStatus").isString())
                .andExpect(jsonPath("$.alerts").isArray())
                .andExpect(jsonPath("$.recentEvents").isArray())
                .andExpect(jsonPath("$.recentAuditLogs").isArray())
                .andExpect(jsonPath("$.flags").isArray());
    }

    @Test
    @DisplayName("Control Center - Client role should return 403 Forbidden")
    @WithMockUser(username = "client@test.com", roles = "CLIENT")
    void testGetControlCenter_WithClientRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/control-center"))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    // ========== /api/admin/events Tests ==========

    @Test
    @DisplayName("Events - Admin role should return paginated normalized events")
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void testGetEvents_WithAdminRole_ShouldReturnPage() throws Exception {
        mockMvc.perform(get("/api/admin/events")
                        .param("category", "AUTH")
                        .param("severity", "INFO")
                        .param("size", "5"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.pageable").exists());
    }

    @Test
    @DisplayName("Events - Freelancer role should return 403 Forbidden")
    @WithMockUser(username = "freelancer@test.com", roles = "FREELANCER")
    void testGetEvents_WithFreelancerRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/events"))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    // ========== /api/admin/flags and audit filters Tests ==========

    @Test
    @DisplayName("Flags - Admin role should return read-only capability flags")
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void testGetFlags_WithAdminRole_ShouldReturnFlags() throws Exception {
        mockMvc.perform(get("/api/admin/flags"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.key == 'tenant-header-required')]").exists())
                .andExpect(jsonPath("$[?(@.key == 'admin-impersonation')]").exists());
    }

    @Test
    @DisplayName("Audit Logs - Admin role can filter by action and anchored state")
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void testGetAuditLogs_WithFilters_ShouldReturnPage() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("action", "LOGIN_FAILED")
                        .param("anchored", "false")
                        .param("tenantId", "default"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("Audit anchors - Admin can list batches and run an empty local cycle")
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void testAuditAnchors_WithAdminRole_ShouldExposeAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/audit-anchor-batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(post("/api/admin/audit-anchor-batches/run"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Audit anchors - Client cannot access proof operations")
    @WithMockUser(username = "client@test.com", roles = "CLIENT")
    void testAuditAnchors_WithClientRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/audit-anchor-batches"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/audit-logs/1/verify"))
                .andExpect(status().isForbidden());
    }
}
