package com.clienthub.application.service;

import com.clienthub.common.context.TenantContext;
import com.clienthub.domain.entity.Project;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.ProjectStatus;
import com.clienthub.domain.enums.Role;
import com.clienthub.application.dto.project.ProjectRequest;
import com.clienthub.application.dto.project.ProjectResponse;
import com.clienthub.application.exception.ResourceNotFoundException;
import com.clienthub.application.mapper.ProjectMapper;
import com.clienthub.domain.repository.ProjectMemberRepository;
import com.clienthub.domain.repository.ProjectRepository;
import com.clienthub.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private ProjectMapper projectMapper;

    @Mock
    private NotificationProducerService notificationProducerService;

    @InjectMocks
    private ProjectService projectService;

    private static final String TENANT_ID = "test-tenant";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("CHDEV-81: Should create project successfully")
    void createProject_Success() {
        ProjectRequest request = new ProjectRequest();
        request.setTitle("New Project");
        request.setBudget(new BigDecimal("1000"));
        request.setDeadline(LocalDate.now().plusDays(30));

        User owner = createUser(USER_ID, TENANT_ID);

        Project projectEntity = new Project();
        projectEntity.setTenantId(TENANT_ID);

        Project savedProject = new Project();
        savedProject.setId(PROJECT_ID);
        savedProject.setStatus(ProjectStatus.PLANNING);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(owner));
        when(projectMapper.toEntity(request)).thenReturn(projectEntity);
        when(projectRepository.save(any(Project.class))).thenReturn(savedProject);
        when(projectMapper.toResponse(savedProject)).thenReturn(new ProjectResponse());

        ProjectResponse response = projectService.createProject(request, USER_ID);

        assertNotNull(response);
        verify(projectRepository).save(any(Project.class));
        verify(userRepository).findById(USER_ID);
    }

    @Test
    @DisplayName("CHDEV-82: Should throw exception when project not found")
    void getProject_NotFound() {
        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> projectService.getProjectById(PROJECT_ID, USER_ID, Role.CLIENT));
    }

    @Test
    @DisplayName("FR03: Project-owning client can read direct project detail")
    void getProjectById_OwningClient_ShouldSucceed() {
        User owner = createUser(USER_ID, TENANT_ID, Role.CLIENT);
        Project project = createProject(owner);
        ProjectResponse expected = new ProjectResponse();

        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID)).thenReturn(Optional.of(project));
        when(projectMapper.toResponse(project)).thenReturn(expected);

        assertSame(expected, projectService.getProjectById(PROJECT_ID, USER_ID, Role.CLIENT));
        verifyNoInteractions(projectMemberRepository);
    }

    @Test
    @DisplayName("FR03: Project-member freelancer can read direct project detail")
    void getProjectById_MemberFreelancer_ShouldSucceed() {
        UUID freelancerId = UUID.randomUUID();
        Project project = createProject(createUser(USER_ID, TENANT_ID, Role.CLIENT));
        ProjectResponse expected = new ProjectResponse();

        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID)).thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByIdProjectIdAndIdUserIdAndTenantId(
                PROJECT_ID, freelancerId, TENANT_ID)).thenReturn(true);
        when(projectMapper.toResponse(project)).thenReturn(expected);

        assertSame(expected, projectService.getProjectById(PROJECT_ID, freelancerId, Role.FREELANCER));
    }

    @Test
    @DisplayName("FR03: Tenant administrator can read direct project detail")
    void getProjectById_Admin_ShouldSucceed() {
        UUID adminId = UUID.randomUUID();
        Project project = createProject(createUser(USER_ID, TENANT_ID, Role.CLIENT));
        ProjectResponse expected = new ProjectResponse();

        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID)).thenReturn(Optional.of(project));
        when(projectMapper.toResponse(project)).thenReturn(expected);

        assertSame(expected, projectService.getProjectById(PROJECT_ID, adminId, Role.ADMIN));
        verifyNoInteractions(projectMemberRepository);
    }

    @Test
    @DisplayName("FR03: Unrelated same-tenant freelancer is denied direct project detail")
    void getProjectById_UnrelatedSameTenantFreelancer_ShouldBeDenied() {
        UUID outsiderId = UUID.randomUUID();
        Project project = createProject(createUser(USER_ID, TENANT_ID, Role.CLIENT));

        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID)).thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByIdProjectIdAndIdUserIdAndTenantId(
                PROJECT_ID, outsiderId, TENANT_ID)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> projectService.getProjectById(PROJECT_ID, outsiderId, Role.FREELANCER));
        verify(projectMapper, never()).toResponse(any(Project.class));
    }

    @Test
    @DisplayName("FR03: Cross-tenant project detail is non-disclosing not found")
    void getProjectById_CrossTenant_ShouldBeNotFound() {
        String otherTenant = "other-tenant";
        TenantContext.setTenantId(otherTenant);
        when(projectRepository.findByIdAndTenantId(PROJECT_ID, otherTenant)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> projectService.getProjectById(PROJECT_ID, USER_ID, Role.CLIENT));
        verifyNoInteractions(projectMemberRepository);
        verify(projectMapper, never()).toResponse(any(Project.class));
    }

    @Test
    @DisplayName("FR03: Client membership cannot escalate into project-owner read access")
    void getProjectById_ClientMembershipPrivilegeEscalation_ShouldBeDenied() {
        UUID unrelatedClientId = UUID.randomUUID();
        Project project = createProject(createUser(USER_ID, TENANT_ID, Role.CLIENT));

        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID)).thenReturn(Optional.of(project));

        assertThrows(AccessDeniedException.class,
                () -> projectService.getProjectById(PROJECT_ID, unrelatedClientId, Role.CLIENT));
        verifyNoInteractions(projectMemberRepository);
        verify(projectMapper, never()).toResponse(any(Project.class));
    }

    @Test
    @DisplayName("Security: Should prevent creating project for user in different tenant")
    void createProject_CrossTenantUser_ShouldThrowException() {
        User crossTenantUser = createUser(USER_ID, "OTHER_TENANT");

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(crossTenantUser));

        assertThrows(AccessDeniedException.class,
                () -> projectService.createProject(new ProjectRequest(), USER_ID));

        verify(projectRepository, never()).save(any());
    }

    @Test
    @DisplayName("Security: Update Project - Only owner can update")
    void updateProject_NotOwner_ShouldThrowException() {
        User owner = createUser(UUID.randomUUID(), TENANT_ID);

        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setOwner(owner);
        project.setTenantId(TENANT_ID);

        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.of(project));

        assertThrows(AccessDeniedException.class,
                () -> projectService.updateProject(PROJECT_ID, new ProjectRequest(), USER_ID, false));
    }

    @Test
    @DisplayName("State Machine: Should prevent invalid status transition")
    void updateProject_InvalidStatus_ShouldThrowException() {
        User owner = createUser(USER_ID, TENANT_ID);

        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setOwner(owner);
        project.setStatus(ProjectStatus.COMPLETED);
        project.setTenantId(TENANT_ID);

        ProjectRequest request = new ProjectRequest();
        request.setStatus(ProjectStatus.IN_PROGRESS);

        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.of(project));

        assertThrows(IllegalStateException.class,
                () -> projectService.updateProject(PROJECT_ID, request, USER_ID, false));
    }

    private User createUser(UUID id, String tenantId) {
        return createUser(id, tenantId, null);
    }

    private User createUser(UUID id, String tenantId, Role role) {
        try {
            User user = new User();
            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
            user.setTenantId(tenantId);
            user.setRole(role);
            return user;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test user", e);
        }
    }

    private Project createProject(User owner) {
        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setOwner(owner);
        project.setTenantId(TENANT_ID);
        return project;
    }
}
