package com.clienthub.web.controller;

import com.clienthub.web.ClientHubBackendApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ClientHubBackendApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("h2test")
class TaskDashboardAuthorizationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("FR05: unauthenticated task list, summary and detail requests return 401")
    void unauthenticatedTaskReadsAreRejected() throws Exception {
        UUID taskId = UUID.randomUUID();

        mockMvc.perform(get("/api/tasks").header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tasks/summary").header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tasks/{id}", taskId).header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("FR05: unauthenticated task mutations return 401")
    void unauthenticatedTaskMutationsAreRejected() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/tasks").header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/tasks/{id}", taskId).header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/tasks/{id}/status", taskId)
                        .param("status", "IN_PROGRESS")
                        .header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/tasks/{id}/assign", taskId)
                        .param("userId", userId.toString())
                        .header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/tasks/{id}/unassign", taskId)
                        .header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/tasks/{id}", taskId)
                        .header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("FR06/NFR01: unauthenticated dashboard summary request returns 401")
    void unauthenticatedDashboardSummaryIsRejected() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")
                        .header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "FREELANCER")
    @DisplayName("FR05: Freelancer cannot invoke owner/Admin-only assign or delete routes")
    void freelancerCannotInvokeOwnerOnlyTaskMutations() throws Exception {
        UUID taskId = UUID.randomUUID();

        mockMvc.perform(patch("/api/tasks/{id}/assign", taskId)
                        .param("userId", UUID.randomUUID().toString())
                        .header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/tasks/{id}", taskId)
                        .header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isForbidden());
    }
}
