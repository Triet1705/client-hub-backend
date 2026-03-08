package com.clienthub.application.service;

import com.clienthub.common.context.TenantContext;
import com.clienthub.domain.enums.InvoiceStatus;
import com.clienthub.domain.enums.ProjectStatus;
import com.clienthub.domain.enums.TaskStatus;
import com.clienthub.application.dto.dashboard.DashboardStatsResponse;
import com.clienthub.domain.repository.InvoiceRepository;
import com.clienthub.domain.repository.ProjectRepository;
import com.clienthub.domain.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final InvoiceRepository invoiceRepository;

    public DashboardService(ProjectRepository projectRepository, TaskRepository taskRepository, InvoiceRepository invoiceRepository) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.invoiceRepository = invoiceRepository;
    }

    public DashboardStatsResponse getSummaryStats() {
        String tenantId = TenantContext.getTenantId();

        long activeProject = projectRepository.countByTenantIdAndStatusIn(
                tenantId, Arrays.asList(ProjectStatus.PLANNING, ProjectStatus.IN_PROGRESS)
        );

        long pendingTasks = taskRepository.countByTenantIdAndStatusIn(
                tenantId, Arrays.asList(TaskStatus.TODO, TaskStatus.IN_PROGRESS)
        );

        BigDecimal awaitingPayment = invoiceRepository.sumAmountByTenantIdAndStatuses(
                tenantId, List.of(InvoiceStatus.SENT)
        );

        BigDecimal escrowLocked = BigDecimal.ZERO;

        return new DashboardStatsResponse(activeProject, pendingTasks, awaitingPayment, escrowLocked);
    }
}
