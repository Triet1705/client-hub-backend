package com.clienthub.core.service;

import com.clienthub.common.context.TenantContext;
import com.clienthub.core.domain.entity.AuditLog;
import com.clienthub.core.domain.enums.AuditAction;
import com.clienthub.core.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AuditService auditService;

    @Captor
    private ArgumentCaptor<AuditLog> auditLogCaptor;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("test-tenant");
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should save AuditLog with correct Data Hash")
    void testLog_Success() throws JsonProcessingException {
        Object payload = new Object();
        String jsonPayload = "{\"key\":\"value\"}";
        when(objectMapper.writeValueAsString(payload)).thenReturn(jsonPayload);

        auditService.log(AuditAction.CREATE, "TASK", "123", null, payload, "127.0.0.1");

        verify(auditLogRepository).save(auditLogCaptor.capture());
        AuditLog savedLog = auditLogCaptor.getValue();

        assertEquals("test-tenant", savedLog.getTenantId());
        assertEquals("TASK", savedLog.getEntityType());
        assertEquals("123", savedLog.getEntityId());
        assertEquals(jsonPayload, savedLog.getNewValue());

        assertNotNull(savedLog.getDataHash());
        assertFalse(savedLog.getDataHash().isEmpty());
    }

    @Test
    @DisplayName("Should handle serialization errors safely")
    void testLog_SerializationError() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("JSON Error"));

        auditService.log(AuditAction.UPDATE, "TASK", "123", null, new Object(), "127.0.0.1");

        verify(auditLogRepository).save(auditLogCaptor.capture());
        AuditLog savedLog = auditLogCaptor.getValue();

        assertTrue(savedLog.getNewValue().contains("Serialization Failed"));
    }
}