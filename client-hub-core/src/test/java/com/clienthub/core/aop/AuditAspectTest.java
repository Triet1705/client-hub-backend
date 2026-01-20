package com.clienthub.core.aop;

import com.clienthub.core.domain.enums.AuditAction;
import com.clienthub.core.dto.task.TaskResponse;
import com.clienthub.core.service.AuditService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditAspectTest {
    @Mock
    private AuditService auditService;

    @Mock
    private JoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @InjectMocks
    private AuditAspect auditAspect;

    private LogAudit logAudit;
    private UUID taskId;

    @BeforeEach
    void setUp() {
        taskId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    @DisplayName("Should parse EntityId from Method Argument (#taskId)")
    void testLogAudit_FromArgument() {
        logAudit = mock(LogAudit.class);
        when(logAudit.action()).thenReturn(AuditAction.UPDATE);
        when(logAudit.entityType()).thenReturn("TASK");
        when(logAudit.entityId()).thenReturn("#taskId");

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"taskId", "request"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{taskId, "someRequest"});

        auditAspect.logAuditActivity(joinPoint, logAudit, null);

        verify(auditService).log(
                eq(AuditAction.UPDATE),
                eq("TASK"),
                eq(taskId.toString()),
                eq(null),
                eq(null),
                eq("127.0.0.1")
        );
    }

    @Test
    @DisplayName("Should parse EntityId from Return Value (#result.id)")
    void testLogAudit_FromResult() {
        logAudit = mock(LogAudit.class);
        when(logAudit.action()).thenReturn(AuditAction.CREATE);
        when(logAudit.entityType()).thenReturn("TASK");
        when(logAudit.entityId()).thenReturn("#result.id");

        TaskResponse response = new TaskResponse();
        response.setId(taskId);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"request"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{"someRequest"});

        auditAspect.logAuditActivity(joinPoint, logAudit, response);

        verify(auditService).log(
                eq(AuditAction.CREATE),
                eq("TASK"),
                eq(taskId.toString()),
                eq(null),
                eq(response),
                eq("127.0.0.1")
        );
    }

    @Test
    @DisplayName("Should handle SpEL parsing error gracefully")
    void testLogAudit_SpELFailure() {
        logAudit = mock(LogAudit.class);
        when(logAudit.action()).thenReturn(AuditAction.DELETE);
        when(logAudit.entityType()).thenReturn("TASK");
        when(logAudit.entityId()).thenReturn("#invalidVar");

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getParameterNames()).thenReturn(new String[]{});
        when(joinPoint.getArgs()).thenReturn(new Object[]{});

        auditAspect.logAuditActivity(joinPoint, logAudit, null);

        verify(auditService).log(
                any(), any(),
                eq("UNKNOWN"),
                any(), any(), any()
        );
    }
}
