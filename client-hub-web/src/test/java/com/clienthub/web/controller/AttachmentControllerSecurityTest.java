package com.clienthub.web.controller;

import com.clienthub.web.ClientHubBackendApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ClientHubBackendApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("h2test")
class AttachmentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("B03b: unauthenticated target-bound upload is rejected")
    void unauthenticatedAttachmentUploadIsRejected() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "scope.pdf", "application/pdf", "test".getBytes());

        mockMvc.perform(multipart("/api/attachments/upload")
                        .file(file)
                        .param("targetType", "PROJECT")
                        .param("targetId", UUID.randomUUID().toString())
                        .header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("B03b: unauthenticated attachment download is rejected")
    void unauthenticatedAttachmentDownloadIsRejected() throws Exception {
        mockMvc.perform(get("/api/attachments/{attachmentId}", UUID.randomUUID())
                        .header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isUnauthorized());
    }
}
