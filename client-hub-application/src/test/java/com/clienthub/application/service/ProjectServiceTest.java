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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
    private static final Pageable PAGEABLE = PageRequest.of(0, 10);

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
    @DisplayName("FR03: Client project list is owner-scoped")
    void getProjects_Client_ShouldUseOwnerScope() {
        Project project = createProject(createUser(USER_ID, TENANT_ID, Role.CLIENT));
        ProjectResponse expected = new ProjectResponse();
        when(projectRepository.findOwnerProjectsByUserIdAndTenantId(USER_ID, TENANT_ID, PAGEABLE))
                .thenReturn(new PageImpl<>(List.of(project)));
        when(projectMapper.toResponse(project)).thenReturn(expected);

        Page<ProjectResponse> result = projectService.getProjects(null, PAGEABLE, USER_ID, Role.CLIENT);

        assertEquals(List.of(expected), result.getContent());
        verify(projectRepository).findOwnerProjectsByUserIdAndTenantId(USER_ID, TENANT_ID, PAGEABLE);
        verify(projectRepository, never()).findAllByTenantId(anyString(), any(Pageable.class));
    }

    @Test
    @DisplayName("FR03: Client cannot list another same-tenant Client's project")
    void getProjects_Client_ShouldNotReceiveOtherClientProjects() {
        when(projectRepository.findOwnerProjectsByUserIdAndTenantId(USER_ID, TENANT_ID, PAGEABLE))
                .thenReturn(Page.empty(PAGEABLE));

        Page<ProjectResponse> result = projectService.getProjects(null, PAGEABLE, USER_ID, Role.CLIENT);

        assertTrue(result.isEmpty());
        verify(projectRepository).findOwnerProjectsByUserIdAndTenantId(USER_ID, TENANT_ID, PAGEABLE);
        verifyNoInteractions(projectMapper);
    }

    @Test
    @DisplayName("FR03: Freelancer project list is explicit-membership scoped")
    void getProjects_Freelancer_ShouldUseMembershipScope() {
        UUID freelancerId = UUID.randomUUID();
        Project project = createProject(createUser(USER_ID, TENANT_ID, Role.CLIENT));
        ProjectResponse expected = new ProjectResponse();
        when(projectRepository.findMemberProjectsByUserIdAndTenantId(freelancerId, TENANT_ID, PAGEABLE))
                .thenReturn(new PageImpl<>(List.of(project)));
        when(projectMapper.toResponse(project)).thenReturn(expected);

        Page<ProjectResponse> result = projectService.getProjects(
                null, PAGEABLE, freelancerId, Role.FREELANCER);

        assertEquals(List.of(expected), result.getContent());
        verify(projectRepository).findMemberProjectsByUserIdAndTenantId(
                freelancerId, TENANT_ID, PAGEABLE);
    }

    @Test
    @DisplayName("FR03: Unrelated same-tenant Freelancer receives an empty project list")
    void getProjects_UnrelatedFreelancer_ShouldReceiveEmptyList() {
        UUID freelancerId = UUID.randomUUID();
        when(projectRepository.findMemberProjectsByUserIdAndTenantId(freelancerId, TENANT_ID, PAGEABLE))
                .thenReturn(Page.empty(PAGEABLE));

        Page<ProjectResponse> result = projectService.getProjects(
                null, PAGEABLE, freelancerId, Role.FREELANCER);

        assertTrue(result.isEmpty());
        verifyNoInteractions(projectMapper);
    }

    @Test
    @DisplayName("FR03: Administrator project list is tenant-wide")
    void getProjects_Admin_ShouldUseTenantScope() {
        UUID adminId = UUID.randomUUID();
        Project project = createProject(createUser(USER_ID, TENANT_ID, Role.CLIENT));
        ProjectResponse expected = new ProjectResponse();
        when(projectRepository.findAllByTenantId(TENANT_ID, PAGEABLE))
                .thenReturn(new PageImpl<>(List.of(project)));
        when(projectMapper.toResponse(project)).thenReturn(expected);

        Page<ProjectResponse> result = projectService.getProjects(
                null, PAGEABLE, adminId, Role.ADMIN);

        assertEquals(List.of(expected), result.getContent());
        verify(projectRepository).findAllByTenantId(TENANT_ID, PAGEABLE);
    }

    @Test
    @DisplayName("FR03: Missing or unsupported role cannot list projects")
    void getProjects_MissingRole_ShouldBeDenied() {
        assertThrows(AccessDeniedException.class,
                () -> projectService.getProjects(null, PAGEABLE, USER_ID, null));

        verifyNoInteractions(projectRepository, projectMapper);
    }

    @Test
    @DisplayName("FR03: Cross-tenant projects remain absent from a Client list")
    void getProjects_ClientCrossTenant_ShouldRemainAbsent() {
        String otherTenant = "other-tenant";
        TenantContext.setTenantId(otherTenant);
        when(projectRepository.findOwnerProjectsByUserIdAndTenantId(USER_ID, otherTenant, PAGEABLE))
                .thenReturn(Page.empty(PAGEABLE));

        Page<ProjectResponse> result = projectService.getProjects(
                null, PAGEABLE, USER_ID, Role.CLIENT);

        assertTrue(result.isEmpty());
        verify(projectRepository).findOwnerProjectsByUserIdAndTenantId(
                USER_ID, otherTenant, PAGEABLE);
    }

    @Test
    @DisplayName("FR03: Client status filter remains owner-scoped")
    void getProjects_ClientStatusFilter_ShouldNotWidenScope() {
        when(projectRepository.findOwnerProjectsByUserIdAndTenantIdAndStatus(
                USER_ID, TENANT_ID, ProjectStatus.IN_PROGRESS, PAGEABLE))
                .thenReturn(Page.empty(PAGEABLE));

        Page<ProjectResponse> result = projectService.getProjects(
                ProjectStatus.IN_PROGRESS, PAGEABLE, USER_ID, Role.CLIENT);

        assertTrue(result.isEmpty());
        verify(projectRepository).findOwnerProjectsByUserIdAndTenantIdAndStatus(
                USER_ID, TENANT_ID, ProjectStatus.IN_PROGRESS, PAGEABLE);
        verify(projectRepository, never()).findByTenantIdAndStatus(
                anyString(), any(ProjectStatus.class), any(Pageable.class));
    }

    @Test
    @DisplayName("FR03: Freelancer status filter remains membership-scoped")
    void getProjects_FreelancerStatusFilter_ShouldNotWidenScope() {
        UUID freelancerId = UUID.randomUUID();
        when(projectRepository.findMemberProjectsByUserIdAndTenantIdAndStatus(
                freelancerId, TENANT_ID, ProjectStatus.COMPLETED, PAGEABLE))
                .thenReturn(Page.empty(PAGEABLE));

        Page<ProjectResponse> result = projectService.getProjects(
                ProjectStatus.COMPLETED, PAGEABLE, freelancerId, Role.FREELANCER);

        assertTrue(result.isEmpty());
        verify(projectRepository).findMemberProjectsByUserIdAndTenantIdAndStatus(
                freelancerId, TENANT_ID, ProjectStatus.COMPLETED, PAGEABLE);
    }

    @Test
    @DisplayName("FR03: Administrator status filter remains tenant-scoped")
    void getProjects_AdminStatusFilter_ShouldUseTenantScope() {
        UUID adminId = UUID.randomUUID();
        when(projectRepository.findByTenantIdAndStatus(
                TENANT_ID, ProjectStatus.ON_HOLD, PAGEABLE))
                .thenReturn(Page.empty(PAGEABLE));

        Page<ProjectResponse> result = projectService.getProjects(
                ProjectStatus.ON_HOLD, PAGEABLE, adminId, Role.ADMIN);

        assertTrue(result.isEmpty());
        verify(projectRepository).findByTenantIdAndStatus(
                TENANT_ID, ProjectStatus.ON_HOLD, PAGEABLE);
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
    @DisplayName("FR04: Owning Client can list project members")
    void getProjectMembers_OwningClient_ShouldAllow() {
        Project project = createProject(createUser(USER_ID, TENANT_ID, Role.CLIENT));
        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.of(project));
        when(projectMemberRepository.findByIdProjectIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(List.of());

        assertTrue(projectService.getProjectMembers(PROJECT_ID, USER_ID, Role.CLIENT).isEmpty());
        verify(projectMemberRepository, never())
                .existsByIdProjectIdAndIdUserIdAndTenantId(any(), any(), anyString());
    }

    @Test
    @DisplayName("FR04: Explicit member Freelancer can list project members")
    void getProjectMembers_MemberFreelancer_ShouldAllow() {
        UUID freelancerId = UUID.randomUUID();
        Project project = createProject(createUser(USER_ID, TENANT_ID, Role.CLIENT));
        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByIdProjectIdAndIdUserIdAndTenantId(
                PROJECT_ID, freelancerId, TENANT_ID)).thenReturn(true);
        when(projectMemberRepository.findByIdProjectIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(List.of());

        assertTrue(projectService.getProjectMembers(
                PROJECT_ID, freelancerId, Role.FREELANCER).isEmpty());
    }

    @Test
    @DisplayName("FR04: Administrator can list project members")
    void getProjectMembers_Admin_ShouldAllow() {
        UUID adminId = UUID.randomUUID();
        Project project = createProject(createUser(USER_ID, TENANT_ID, Role.CLIENT));
        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.of(project));
        when(projectMemberRepository.findByIdProjectIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(List.of());

        assertTrue(projectService.getProjectMembers(PROJECT_ID, adminId, Role.ADMIN).isEmpty());
        verify(projectMemberRepository, never())
                .existsByIdProjectIdAndIdUserIdAndTenantId(any(), any(), anyString());
    }

    @Test
    @DisplayName("FR04: Client membership row cannot grant member-list access")
    void getProjectMembers_ClientMembershipEscalation_ShouldBeDenied() {
        UUID unrelatedClientId = UUID.randomUUID();
        Project project = createProject(createUser(USER_ID, TENANT_ID, Role.CLIENT));
        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.of(project));

        assertThrows(AccessDeniedException.class,
                () -> projectService.getProjectMembers(
                        PROJECT_ID, unrelatedClientId, Role.CLIENT));
        verify(projectMemberRepository, never())
                .existsByIdProjectIdAndIdUserIdAndTenantId(any(), any(), anyString());
        verify(projectMemberRepository, never()).findByIdProjectIdAndTenantId(any(), anyString());
    }

    @Test
    @DisplayName("FR04: Owner UUID with the wrong role cannot list project members")
    void getProjectMembers_OwnerUuidWithWrongRole_ShouldBeDenied() {
        Project project = createProject(createUser(USER_ID, TENANT_ID, Role.CLIENT));
        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByIdProjectIdAndIdUserIdAndTenantId(
                PROJECT_ID, USER_ID, TENANT_ID)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> projectService.getProjectMembers(
                        PROJECT_ID, USER_ID, Role.FREELANCER));
    }

    @Test
    @DisplayName("FR04: Same-tenant outsider cannot list project members")
    void getProjectMembers_SameTenantOutsider_ShouldBeDenied() {
        UUID outsiderId = UUID.randomUUID();
        Project project = createProject(createUser(USER_ID, TENANT_ID, Role.CLIENT));
        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByIdProjectIdAndIdUserIdAndTenantId(
                PROJECT_ID, outsiderId, TENANT_ID)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> projectService.getProjectMembers(
                        PROJECT_ID, outsiderId, Role.FREELANCER));
    }

    @Test
    @DisplayName("FR04: Missing and cross-tenant projects share non-disclosing not found")
    void getProjectMembers_CrossTenant_ShouldBeNotFound() {
        String otherTenant = "other-tenant";
        TenantContext.setTenantId(otherTenant);
        when(projectRepository.findByIdAndTenantId(PROJECT_ID, otherTenant))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> projectService.getProjectMembers(PROJECT_ID, USER_ID, Role.CLIENT));
        verifyNoInteractions(projectMemberRepository);
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
