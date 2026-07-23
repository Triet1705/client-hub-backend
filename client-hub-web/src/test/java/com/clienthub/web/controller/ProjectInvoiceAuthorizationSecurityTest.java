package com.clienthub.web.controller;

import com.clienthub.web.ClientHubBackendApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ClientHubBackendApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("h2test")
class ProjectInvoiceAuthorizationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("FR03/FR04: unauthenticated project list and member-list requests are rejected")
    void unauthenticatedProjectListAndMembersAreRejected() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(get("/api/projects")
                        .header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/projects/{id}/members", projectId)
                        .header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("FR10/FR15: unauthenticated project portal and proof requests are rejected")
    void unauthenticatedProjectPortalAndProofAreRejected() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(get("/api/projects/{id}/files", projectId)
                        .header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/projects/{id}/activity", projectId)
                        .header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/projects/{id}/activity/{auditLogId}/proof", projectId, 42L)
                        .header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/projects/{id}/activity/{auditLogId}/verify", projectId, 42L)
                        .header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("FR09: unauthenticated invoice read and status requests are rejected")
    void unauthenticatedInvoiceReadAndStatusAreRejected() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(get("/api/invoices")
                        .header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/invoices/{id}", 10L)
                        .header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/invoices/project/{projectId}", projectId)
                        .header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/invoices/{id}/status", 10L)
                        .header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("FR09/FR15: unauthenticated invoice proof requests are rejected")
    void unauthenticatedInvoiceProofRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/invoices/{id}/audit-proof", 10L)
                        .header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/invoices/{id}/audit-proof/verify", 10L)
                        .header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isUnauthorized());
    }
}
