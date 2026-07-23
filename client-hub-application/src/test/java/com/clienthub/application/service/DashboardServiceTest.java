package com.clienthub.application.service;

import com.clienthub.application.dto.dashboard.DashboardStatsResponse;
import com.clienthub.application.exception.ResourceNotFoundException;
import com.clienthub.common.context.TenantContext;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.InvoiceStatus;
import com.clienthub.domain.enums.ProjectStatus;
import com.clienthub.domain.enums.Role;
import com.clienthub.domain.enums.TaskStatus;
import com.clienthub.domain.repository.InvoiceRepository;
import com.clienthub.domain.repository.ProjectRepository;
import com.clienthub.domain.repository.TaskRepository;
import com.clienthub.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final String TENANT_ID = "tenant-a";
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID FREELANCER_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final List<ProjectStatus> ACTIVE_PROJECTS =
            List.of(ProjectStatus.PLANNING, ProjectStatus.IN_PROGRESS);
    private static final List<TaskStatus> PENDING_TASKS =
            List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS);
    private static final List<InvoiceStatus> AWAITING_PAYMENT =
            List.of(InvoiceStatus.SENT);

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Client dashboard aggregates only owned projects/tasks and invoice-party amounts")
    void getSummaryStats_clientUsesRelationshipScopedAggregates() {
        User client = user(CLIENT_ID, Role.CLIENT);
        when(userRepository.findByIdAndTenantId(CLIENT_ID, TENANT_ID))
                .thenReturn(Optional.of(client));
        when(projectRepository.countOwnerProjectsByUserIdAndTenantIdAndStatusIn(
                CLIENT_ID, TENANT_ID, ACTIVE_PROJECTS)).thenReturn(2L);
        when(taskRepository.countByProjectOwnerIdAndTenantIdAndStatusIn(
                CLIENT_ID, TENANT_ID, PENDING_TASKS)).thenReturn(3L);
        when(invoiceRepository.sumAmountByTenantIdAndClientIdAndStatuses(
                TENANT_ID, CLIENT_ID, AWAITING_PAYMENT)).thenReturn(new BigDecimal("125.50"));

        DashboardStatsResponse result = dashboardService.getSummaryStats(CLIENT_ID);

        assertEquals(2L, result.getActiveProjects());
        assertEquals(3L, result.getPendingTasks());
        assertEquals(new BigDecimal("125.50"), result.getAwaitingPaymentAmount());
        verify(projectRepository, never()).countByTenantIdAndStatusIn(any(), any());
        verify(taskRepository, never()).countByTenantIdAndStatusIn(any(), any());
        verify(invoiceRepository, never()).sumAmountByTenantIdAndStatuses(any(), any());
    }

    @Test
    @DisplayName("Freelancer dashboard uses member projects, assigned tasks and invoice-party amounts")
    void getSummaryStats_freelancerUsesRelationshipScopedAggregates() {
        User freelancer = user(FREELANCER_ID, Role.FREELANCER);
        when(userRepository.findByIdAndTenantId(FREELANCER_ID, TENANT_ID))
                .thenReturn(Optional.of(freelancer));
        when(projectRepository.countMemberProjectsByUserIdAndTenantIdAndStatusIn(
                FREELANCER_ID, TENANT_ID, ACTIVE_PROJECTS)).thenReturn(1L);
        when(taskRepository.countByAssignedToIdAndTenantIdAndStatusIn(
                FREELANCER_ID, TENANT_ID, PENDING_TASKS)).thenReturn(4L);
        when(invoiceRepository.sumAmountByTenantIdAndFreelancerIdAndStatuses(
                TENANT_ID, FREELANCER_ID, AWAITING_PAYMENT)).thenReturn(new BigDecimal("80.00"));

        DashboardStatsResponse result = dashboardService.getSummaryStats(FREELANCER_ID);

        assertEquals(1L, result.getActiveProjects());
        assertEquals(4L, result.getPendingTasks());
        assertEquals(new BigDecimal("80.00"), result.getAwaitingPaymentAmount());
        verify(projectRepository, never()).countByTenantIdAndStatusIn(any(), any());
        verify(taskRepository, never()).countByTenantIdAndStatusIn(any(), any());
    }

    @Test
    @DisplayName("Administrator dashboard remains scoped to authenticated tenant")
    void getSummaryStats_administratorUsesTenantAggregates() {
        User admin = user(ADMIN_ID, Role.ADMIN);
        when(userRepository.findByIdAndTenantId(ADMIN_ID, TENANT_ID))
                .thenReturn(Optional.of(admin));
        when(projectRepository.countByTenantIdAndStatusIn(TENANT_ID, ACTIVE_PROJECTS))
                .thenReturn(7L);
        when(taskRepository.countByTenantIdAndStatusIn(TENANT_ID, PENDING_TASKS))
                .thenReturn(9L);
        when(invoiceRepository.sumAmountByTenantIdAndStatuses(TENANT_ID, AWAITING_PAYMENT))
                .thenReturn(new BigDecimal("999.00"));

        DashboardStatsResponse result = dashboardService.getSummaryStats(ADMIN_ID);

        assertEquals(7L, result.getActiveProjects());
        assertEquals(9L, result.getPendingTasks());
        assertEquals(new BigDecimal("999.00"), result.getAwaitingPaymentAmount());
    }

    @Test
    @DisplayName("Same-tenant outsider receives valid empty relationship-scoped dashboard")
    void getSummaryStats_sameTenantOutsiderReturnsZeroScopedTotals() {
        User client = user(CLIENT_ID, Role.CLIENT);
        when(userRepository.findByIdAndTenantId(CLIENT_ID, TENANT_ID))
                .thenReturn(Optional.of(client));
        when(invoiceRepository.sumAmountByTenantIdAndClientIdAndStatuses(
                TENANT_ID, CLIENT_ID, AWAITING_PAYMENT)).thenReturn(BigDecimal.ZERO);

        DashboardStatsResponse result = dashboardService.getSummaryStats(CLIENT_ID);

        assertEquals(0L, result.getActiveProjects());
        assertEquals(0L, result.getPendingTasks());
        assertEquals(BigDecimal.ZERO, result.getAwaitingPaymentAmount());
    }

    @Test
    @DisplayName("Cross-tenant or missing dashboard actor is non-disclosing")
    void getSummaryStats_crossTenantActorNotFound() {
        when(userRepository.findByIdAndTenantId(CLIENT_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> dashboardService.getSummaryStats(CLIENT_ID));
        verify(projectRepository, never()).countByTenantIdAndStatusIn(any(), any());
        verify(taskRepository, never()).countByTenantIdAndStatusIn(any(), any());
        verify(invoiceRepository, never()).sumAmountByTenantIdAndStatuses(any(), any());
    }

    private User user(UUID id, Role role) {
        return User.builder()
                .id(id)
                .tenantId(TENANT_ID)
                .email(id + "@example.test")
                .password("not-used")
                .fullName(role.name())
                .role(role)
                .active(true)
                .build();
    }
}
