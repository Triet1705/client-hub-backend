package com.clienthub.application.service;

import com.clienthub.application.dto.analytics.ProjectProgressResponse;
import com.clienthub.application.exception.ResourceNotFoundException;
import com.clienthub.common.context.TenantContext;
import com.clienthub.domain.entity.Project;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.Role;
import com.clienthub.domain.enums.TaskStatus;
import com.clienthub.domain.repository.CommunicationThreadRepository;
import com.clienthub.domain.repository.InvoiceRepository;
import com.clienthub.domain.repository.ProjectMemberRepository;
import com.clienthub.domain.repository.ProjectRepository;
import com.clienthub.domain.repository.TaskRepository;
import com.clienthub.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    private static final String TENANT_ID = "tenant-alpha";
    private static final String OTHER_TENANT_ID = "tenant-beta";
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();

    @Mock private TaskRepository taskRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private CommunicationThreadRepository threadRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;

    private AnalyticsService analyticsService;
    private Project project;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        analyticsService = new AnalyticsService(
                taskRepository,
                invoiceRepository,
                threadRepository,
                userRepository,
                projectRepository,
                projectMemberRepository);

        User owner = user(OWNER_ID, Role.CLIENT, TENANT_ID);
        project = new Project();
        project.setId(PROJECT_ID);
        project.setOwner(owner);
        project.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("B03a: owning Client can read project progress")
    void owningClientCanReadProgress() {
        arrangeProjectAndProgressCounts();

        ProjectProgressResponse response =
                analyticsService.getProjectProgress(PROJECT_ID, OWNER_ID, Role.CLIENT);

        assertEquals(50, response.progressPercent());
        assertEquals(2, response.completedTasks());
        assertEquals(4, response.totalTasks());
        verifyNoInteractions(projectMemberRepository);
    }

    @Test
    @DisplayName("B03a: project-member Freelancer can read project progress")
    void memberFreelancerCanReadProgress() {
        UUID memberId = UUID.randomUUID();
        arrangeProjectAndProgressCounts();
        when(projectMemberRepository.existsByIdProjectIdAndIdUserIdAndTenantId(
                PROJECT_ID, memberId, TENANT_ID)).thenReturn(true);

        ProjectProgressResponse response =
                analyticsService.getProjectProgress(PROJECT_ID, memberId, Role.FREELANCER);

        assertEquals(50, response.progressPercent());
    }

    @Test
    @DisplayName("B03a: tenant Administrator can read project progress")
    void administratorCanReadProgress() {
        arrangeProjectAndProgressCounts();

        ProjectProgressResponse response =
                analyticsService.getProjectProgress(PROJECT_ID, UUID.randomUUID(), Role.ADMIN);

        assertEquals(50, response.progressPercent());
        verifyNoInteractions(projectMemberRepository);
    }

    @Test
    @DisplayName("B03a: unrelated same-tenant Client is denied")
    void unrelatedSameTenantClientIsDenied() {
        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.of(project));

        assertThrows(
                AccessDeniedException.class,
                () -> analyticsService.getProjectProgress(
                        PROJECT_ID, UUID.randomUUID(), Role.CLIENT));

        verifyNoInteractions(projectMemberRepository);
        verifyNoTaskCounts();
    }

    @Test
    @DisplayName("B03a: unrelated same-tenant Freelancer is denied")
    void unrelatedSameTenantFreelancerIsDenied() {
        UUID outsiderId = UUID.randomUUID();
        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByIdProjectIdAndIdUserIdAndTenantId(
                PROJECT_ID, outsiderId, TENANT_ID)).thenReturn(false);

        assertThrows(
                AccessDeniedException.class,
                () -> analyticsService.getProjectProgress(
                        PROJECT_ID, outsiderId, Role.FREELANCER));

        verifyNoTaskCounts();
    }

    @Test
    @DisplayName("B03a: Client cannot escalate access through project membership")
    void clientMembershipCannotEscalateAccess() {
        UUID unrelatedClientId = UUID.randomUUID();
        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.of(project));

        assertThrows(
                AccessDeniedException.class,
                () -> analyticsService.getProjectProgress(
                        PROJECT_ID, unrelatedClientId, Role.CLIENT));

        verifyNoInteractions(projectMemberRepository);
        verifyNoTaskCounts();
    }

    @Test
    @DisplayName("B03a: missing and cross-tenant projects are equivalent non-disclosing not-found results")
    void missingAndCrossTenantProjectsAreEquivalent() {
        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        ResourceNotFoundException missing = assertThrows(
                ResourceNotFoundException.class,
                () -> analyticsService.getProjectProgress(PROJECT_ID, OWNER_ID, Role.CLIENT));

        TenantContext.setTenantId(OTHER_TENANT_ID);
        when(projectRepository.findByIdAndTenantId(PROJECT_ID, OTHER_TENANT_ID))
                .thenReturn(Optional.empty());

        ResourceNotFoundException crossTenant = assertThrows(
                ResourceNotFoundException.class,
                () -> analyticsService.getProjectProgress(PROJECT_ID, OWNER_ID, Role.CLIENT));

        assertEquals(missing.getMessage(), crossTenant.getMessage());
        verifyNoInteractions(projectMemberRepository);
        verifyNoTaskCounts();
    }

    private void arrangeProjectAndProgressCounts() {
        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.of(project));
        when(taskRepository.countByProjectIdAndTenantId(PROJECT_ID, TENANT_ID)).thenReturn(5L);
        when(taskRepository.countByProjectIdAndTenantIdAndStatus(
                PROJECT_ID, TENANT_ID, TaskStatus.CANCELED)).thenReturn(1L);
        when(taskRepository.countByProjectIdAndTenantIdAndStatus(
                PROJECT_ID, TENANT_ID, TaskStatus.DONE)).thenReturn(2L);
    }

    private void verifyNoTaskCounts() {
        verify(taskRepository, never()).countByProjectIdAndTenantId(PROJECT_ID, TENANT_ID);
        verify(taskRepository, never()).countByProjectIdAndTenantIdAndStatus(
                PROJECT_ID, TENANT_ID, TaskStatus.CANCELED);
        verify(taskRepository, never()).countByProjectIdAndTenantIdAndStatus(
                PROJECT_ID, TENANT_ID, TaskStatus.DONE);
    }

    private User user(UUID id, Role role, String tenantId) {
        try {
            User user = new User();
            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
            user.setRole(role);
            user.setTenantId(tenantId);
            return user;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not create test user", e);
        }
    }
}
