package com.clienthub.core.service;

import com.clienthub.common.context.TenantContext;
import com.clienthub.core.domain.entity.Project;
import com.clienthub.core.domain.entity.User;
import com.clienthub.core.domain.enums.ProjectStatus;
import com.clienthub.core.dto.project.ProjectRequest;
import com.clienthub.core.dto.project.ProjectResponse;
import com.clienthub.core.exception.ResourceNotFoundException;
import com.clienthub.core.mapper.ProjectMapper;
import com.clienthub.core.repository.ProjectRepository;
import com.clienthub.core.repository.UserRepository;
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
    private ProjectMapper projectMapper;

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
                () -> projectService.getProjectById(PROJECT_ID));
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
                () -> projectService.updateProject(PROJECT_ID, new ProjectRequest(), USER_ID));
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
                () -> projectService.updateProject(PROJECT_ID, request, USER_ID));
    }

    private User createUser(UUID id, String tenantId) {
        try {
            User user = new User();
            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
            user.setTenantId(tenantId);
            return user;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test user", e);
        }
    }
}