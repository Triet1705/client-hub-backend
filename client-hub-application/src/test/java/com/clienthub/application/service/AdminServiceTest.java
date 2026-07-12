package com.clienthub.application.service;

import com.clienthub.application.dto.admin.AdminEventItem;
import com.clienthub.application.dto.admin.AdminFeatureFlag;
import com.clienthub.application.dto.admin.AdminControlCenterResponse;
import com.clienthub.domain.entity.AuditLog;
import com.clienthub.domain.enums.AuditAction;
import com.clienthub.domain.repository.AuditLogRepository;
import com.clienthub.domain.repository.AuditAnchorMemberRepository;
import com.clienthub.domain.repository.InvoiceRepository;
import com.clienthub.domain.repository.ProjectRepository;
import com.clienthub.domain.repository.UserRepository;
import com.clienthub.infrastructure.security.JwtTokenProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditAnchorMemberRepository auditAnchorMemberRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RestTemplate restTemplate;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(
                userRepository,
                projectRepository,
                invoiceRepository,
                auditLogRepository,
                auditAnchorMemberRepository,
                jwtTokenProvider,
                jdbcTemplate,
                redisTemplate,
                new RestTemplateBuilder());
    }

    @Test
    @DisplayName("Should map runtime config to read-only admin feature flags")
    void getFeatureFlags_ShouldReflectRuntimeConfig() {
        ReflectionTestUtils.setField(adminService, "blockchainEnabled", true);
        ReflectionTestUtils.setField(adminService, "blockchainNodeUrl", "https://polygon-rpc.example");
        ReflectionTestUtils.setField(adminService, "blockchainContractAddress", "0x123");
        ReflectionTestUtils.setField(adminService, "ollamaUrl", "http://localhost:11434");
        ReflectionTestUtils.setField(adminService, "ollamaModel", "llama3.2:3b");
        ReflectionTestUtils.setField(adminService, "tenantHeaderRequired", true);
        ReflectionTestUtils.setField(adminService, "rlsEnabled", true);
        ReflectionTestUtils.setField(adminService, "loginRateLimit", 5);
        ReflectionTestUtils.setField(adminService, "registerRateLimit", 3);

        List<AdminFeatureFlag> flags = adminService.getFeatureFlags();

        assertEquals(7, flags.size());
        assertTrue(flags.stream().anyMatch(flag ->
                flag.key().equals("blockchain-workflows") && flag.enabled() && flag.status().equals("READY")));
        assertTrue(flags.stream().anyMatch(flag ->
                flag.key().equals("tenant-header-required") && flag.status().equals("ENFORCED")));
        assertTrue(flags.stream().anyMatch(flag ->
                flag.key().equals("auth-rate-limits") && flag.enabled() && flag.status().equals("ACTIVE")));
        assertTrue(flags.stream().allMatch(flag -> flag.source() != null && !flag.source().isBlank()));
    }

    @Test
    @DisplayName("Should normalize audit logs into filterable admin domain events")
    void listEvents_ShouldFilterByCategoryAndSeverity() {
        AuditLog login = new AuditLog(
                "default",
                null,
                "admin@test.com",
                "ADMIN",
                AuditAction.LOGIN,
                "USER",
                "user-1",
                null,
                null,
                "127.0.0.1",
                "hash");

        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(login), PageRequest.of(0, 10), 1));

        Page<AdminEventItem> events = adminService.listEvents(
                "AUTH",
                "INFO",
                null,
                null,
                null,
                null,
                PageRequest.of(0, 10));

        assertEquals(1, events.getTotalElements());
        AdminEventItem event = events.getContent().getFirst();
        assertEquals("AUTH", event.category());
        assertEquals("INFO", event.severity());
        assertEquals("Login", event.title());
        assertEquals("default", event.tenantId());
    }

    @Test
    @DisplayName("Should aggregate summary and derive operational alerts")
    void getControlCenter_ShouldAggregateSummaryAndAlerts() {
        ReflectionTestUtils.setField(adminService, "ollamaUrl", "http://localhost:11434");
        ReflectionTestUtils.setField(adminService, "ollamaModel", "llama3.2:3b");
        ReflectionTestUtils.setField(adminService, "restTemplate", restTemplate);

        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenThrow(new RuntimeException("offline"));
        when(invoiceRepository.sumAmountByStatuses(anyList())).thenReturn(BigDecimal.valueOf(1200));
        when(userRepository.countByLastLoginAtAfter(any(Instant.class))).thenReturn(3L);
        when(projectRepository.countByStatusNotIn(anyList())).thenReturn(4L);
        when(invoiceRepository.countByStatusNotIn(anyList())).thenReturn(2L);
        when(auditLogRepository.countByActionAndCreatedAtAfter(eq(AuditAction.LOGIN_FAILED), any(Instant.class)))
                .thenReturn(11L);
        when(auditLogRepository.countWithoutConfirmedAnchor(anyList(), eq(com.clienthub.domain.enums.AuditAnchorBatchStatus.CONFIRMED))).thenReturn(5L);
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        AdminControlCenterResponse response = adminService.getControlCenter();

        assertEquals(BigDecimal.valueOf(1200), response.summary().totalRevenue());
        assertEquals(3L, response.summary().activeUsers24h());
        assertEquals(4L, response.summary().openProjects());
        assertEquals(2L, response.summary().unpaidInvoices());
        assertTrue(response.alerts().stream().anyMatch(alert -> alert.id().equals("failed-logins-24h") && alert.severity().equals("CRITICAL")));
        assertTrue(response.alerts().stream().anyMatch(alert -> alert.id().equals("unanchored-audit-logs")));
        assertTrue(response.alerts().stream().anyMatch(alert -> alert.id().equals("unpaid-invoices")));
    }
}
