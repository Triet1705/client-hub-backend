package com.clienthub.application.service;

import com.clienthub.application.dto.dashboard.DashboardStatsResponse;
import com.clienthub.application.exception.ResourceNotFoundException;
import com.clienthub.common.service.TenantAwareService;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.InvoiceStatus;
import com.clienthub.domain.enums.ProjectStatus;
import com.clienthub.domain.enums.Role;
import com.clienthub.domain.enums.TaskStatus;
import com.clienthub.domain.repository.InvoiceRepository;
import com.clienthub.domain.repository.ProjectRepository;
import com.clienthub.domain.repository.TaskRepository;
import com.clienthub.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DashboardService extends TenantAwareService {

    private static final List<ProjectStatus> ACTIVE_PROJECT_STATUSES =
            List.of(ProjectStatus.PLANNING, ProjectStatus.IN_PROGRESS);
    private static final List<TaskStatus> PENDING_TASK_STATUSES =
            List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS);
    private static final List<InvoiceStatus> AWAITING_PAYMENT_STATUSES =
            List.of(InvoiceStatus.SENT);

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;

    public DashboardService(ProjectRepository projectRepository,
                            TaskRepository taskRepository,
                            InvoiceRepository invoiceRepository,
                            UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.invoiceRepository = invoiceRepository;
        this.userRepository = userRepository;
    }

    public DashboardStatsResponse getSummaryStats(UUID currentUserId) {
        String tenantId = getCurrentTenantId();
        User actor = userRepository.findByIdAndTenantId(currentUserId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));

        long activeProjects = countActiveProjects(actor, tenantId);
        long pendingTasks = countPendingTasks(actor, tenantId);
        BigDecimal awaitingPayment = sumAwaitingPayment(actor, tenantId);

        return new DashboardStatsResponse(
                activeProjects, pendingTasks, awaitingPayment, BigDecimal.ZERO);
    }

    private long countActiveProjects(User actor, String tenantId) {
        return switch (actor.getRole()) {
            case CLIENT -> projectRepository.countOwnerProjectsByUserIdAndTenantIdAndStatusIn(
                    actor.getId(), tenantId, ACTIVE_PROJECT_STATUSES);
            case FREELANCER -> projectRepository.countMemberProjectsByUserIdAndTenantIdAndStatusIn(
                    actor.getId(), tenantId, ACTIVE_PROJECT_STATUSES);
            case ADMIN -> projectRepository.countByTenantIdAndStatusIn(
                    tenantId, ACTIVE_PROJECT_STATUSES);
        };
    }

    private long countPendingTasks(User actor, String tenantId) {
        return switch (actor.getRole()) {
            case CLIENT -> taskRepository.countByProjectOwnerIdAndTenantIdAndStatusIn(
                    actor.getId(), tenantId, PENDING_TASK_STATUSES);
            case FREELANCER -> taskRepository.countByAssignedToIdAndTenantIdAndStatusIn(
                    actor.getId(), tenantId, PENDING_TASK_STATUSES);
            case ADMIN -> taskRepository.countByTenantIdAndStatusIn(
                    tenantId, PENDING_TASK_STATUSES);
        };
    }

    private BigDecimal sumAwaitingPayment(User actor, String tenantId) {
        Role role = actor.getRole();
        if (role == Role.CLIENT) {
            return invoiceRepository.sumAmountByTenantIdAndClientIdAndStatuses(
                    tenantId, actor.getId(), AWAITING_PAYMENT_STATUSES);
        }
        if (role == Role.FREELANCER) {
            return invoiceRepository.sumAmountByTenantIdAndFreelancerIdAndStatuses(
                    tenantId, actor.getId(), AWAITING_PAYMENT_STATUSES);
        }
        return invoiceRepository.sumAmountByTenantIdAndStatuses(
                tenantId, AWAITING_PAYMENT_STATUSES);
    }
}
