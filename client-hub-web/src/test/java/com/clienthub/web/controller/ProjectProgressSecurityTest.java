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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ClientHubBackendApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("h2test")
class ProjectProgressSecurityTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("B03a: unauthenticated project-progress request is rejected at HTTP boundary")
    void unauthenticatedProjectProgressRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/projects/{id}/progress", UUID.randomUUID())
                        .header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isUnauthorized());
    }
}
