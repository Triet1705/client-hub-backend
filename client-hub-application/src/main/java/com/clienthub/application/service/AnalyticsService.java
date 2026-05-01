package com.clienthub.application.service;

import com.clienthub.application.dto.analytics.AdminDashboardResponse;
import com.clienthub.application.dto.analytics.ProjectProgressResponse;
import com.clienthub.application.dto.analytics.ResponseRateResponse;
import com.clienthub.application.dto.analytics.TrustScoreResponse;
import com.clienthub.domain.enums.InvoiceStatus;
import com.clienthub.domain.enums.TaskStatus;
import com.clienthub.domain.repository.CommunicationThreadRepository;
import com.clienthub.domain.repository.InvoiceRepository;
import com.clienthub.domain.repository.ProjectRepository;
import com.clienthub.domain.repository.TaskRepository;
import com.clienthub.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final TaskRepository taskRepository;
    private final InvoiceRepository invoiceRepository;
    private final CommunicationThreadRepository threadRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;

    public AnalyticsService(TaskRepository taskRepository,
                            InvoiceRepository invoiceRepository,
                            CommunicationThreadRepository threadRepository,
                            UserRepository userRepository,
                            ProjectRepository projectRepository) {
        this.taskRepository = taskRepository;
        this.invoiceRepository = invoiceRepository;
        this.threadRepository = threadRepository;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
    }

    public ProjectProgressResponse getProjectProgress(UUID projectId, String tenantId) {
        long totalTasks = taskRepository.countByProjectIdAndTenantId(projectId, tenantId);
        long canceledTasks = taskRepository.countByProjectIdAndTenantIdAndStatus(projectId, tenantId, TaskStatus.CANCELED);
        long doneTasks = taskRepository.countByProjectIdAndTenantIdAndStatus(projectId, tenantId, TaskStatus.DONE);

        long validTotal = totalTasks - canceledTasks;
        int progressPercent = validTotal == 0 ? 0 : (int) ((doneTasks * 100) / validTotal);

        return new ProjectProgressResponse(progressPercent, doneTasks, validTotal);
    }

    public TrustScoreResponse getTrustScore(String tenantId) {
        long paidInvoices = invoiceRepository.countByTenantIdAndStatus(tenantId, InvoiceStatus.PAID);
        long draftInvoices = invoiceRepository.countByTenantIdAndStatus(tenantId, InvoiceStatus.DRAFT);
        long totalInvoices = invoiceRepository.countByTenantId(tenantId);

        long validTotal = totalInvoices - draftInvoices;
        int trustScore = validTotal == 0 ? 100 : (int) ((paidInvoices * 100) / validTotal);

        return new TrustScoreResponse(trustScore, paidInvoices, validTotal, "invoices");
    }

    public ResponseRateResponse getResponseRate(String tenantId) {
        long totalThreads = threadRepository.countByTenantId(tenantId);
        long respondedThreads = threadRepository.countThreadsWithMultipleParticipantsByTenantId(tenantId);

        int responseRate = totalThreads == 0 ? 100 : (int) ((respondedThreads * 100) / totalThreads);

        return new ResponseRateResponse(responseRate, respondedThreads, totalThreads, "percent");
    }

    public AdminDashboardResponse getAdminDashboard(String tenantId) {
        long totalUsers = userRepository.count();
        long totalProjects = projectRepository.countByTenantId(tenantId);
        long totalInvoices = invoiceRepository.countByTenantId(tenantId);
        BigDecimal totalRevenue = invoiceRepository.sumAmountByTenantIdAndStatuses(tenantId, java.util.List.of(InvoiceStatus.PAID));

        return new AdminDashboardResponse(totalUsers, totalProjects, totalInvoices, totalRevenue, "Stable");
    }
}
