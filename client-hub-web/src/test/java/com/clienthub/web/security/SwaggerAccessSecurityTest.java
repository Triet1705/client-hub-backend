package com.clienthub.web.security;

import com.clienthub.web.ClientHubBackendApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ClientHubBackendApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("h2test")
class SwaggerAccessSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Swagger UI and OpenAPI documents load before bearer authorization")
    void swaggerResourcesArePubliclyLoadable() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/swagger-auth/index.html"))
                .andExpect(header().string(
                        "Content-Security-Policy",
                        not(containsString("sandbox"))));

        mockMvc.perform(get("/swagger-auth/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Client Hub API Console")));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/swagger-auth/swagger-login.js"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/swagger-auth/swagger-login.css"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Business APIs still require authentication after Swagger resources are opened")
    void businessApisRemainProtected() throws Exception {
        mockMvc.perform(get("/api/projects")
                        .header("X-Tenant-ID", "tenant-alpha"))
                .andExpect(status().isUnauthorized());
    }
}
