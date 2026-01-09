package com.clienthub.api.controller;

import com.clienthub.api.ClientHubBackendApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    // ========== /api/admin/dashboard Tests ==========
    
    @Test
    @DisplayName("Dashboard - Admin role should return 200 OK")
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void testGetDashboard_WithAdminRole_ShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Authorize"))
                .andExpect(jsonPath("$.message").value("Welcome to Admin Dashboard"));
    }

    @Test
    @DisplayName("Dashboard - Client role should return 403 Forbidden")
    @WithMockUser(username = "client@test.com", roles = "CLIENT")
    void testGetDashboard_WithClientRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Dashboard - Freelancer role should return 403 Forbidden")
    @WithMockUser(username = "freelancer@test.com", roles = "FREELANCER")
    void testGetDashboard_WithFreelancerRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Dashboard - Unauthenticated user should return 401 Unauthorized")
    void testGetDashboard_WithoutAuthentication_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    // ========== /api/admin/system-status Tests ==========
    
    @Test
    @DisplayName("System Status - Admin role should return 200 OK")
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void testGetSystemStatus_WithAdminRole_ShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/admin/system-status"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.system").value("Client Hub Core"))
                .andExpect(jsonPath("$.health").value("Stable"))
                .andExpect(jsonPath("$.active_connections").value(1));
    }

    @Test
    @DisplayName("System Status - Client role should return 403 Forbidden")
    @WithMockUser(username = "client@test.com", roles = "CLIENT")
    void testGetSystemStatus_WithClientRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/system-status"))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("System Status - Freelancer role should return 403 Forbidden")
    @WithMockUser(username = "freelancer@test.com", roles = "FREELANCER")
    void testGetSystemStatus_WithFreelancerRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/system-status"))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("System Status - Unauthenticated user should return 401 Unauthorized")
    void testGetSystemStatus_WithoutAuthentication_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/system-status"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }
}
