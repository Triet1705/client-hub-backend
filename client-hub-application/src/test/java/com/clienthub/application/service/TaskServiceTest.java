package com.clienthub.application.service;

import com.clienthub.common.context.TenantContext;
import com.clienthub.domain.entity.Project;
import com.clienthub.domain.entity.Task;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.ProjectStatus;
import com.clienthub.domain.enums.Role;
import com.clienthub.domain.enums.TaskPriority;
import com.clienthub.domain.enums.TaskStatus;
import com.clienthub.application.dto.task.TaskRequest;
import com.clienthub.application.dto.task.TaskResponse;
import com.clienthub.application.exception.InvalidTaskStateException;
import com.clienthub.application.exception.TaskNotFoundException;
import com.clienthub.application.mapper.TaskMapper;
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
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TaskService.
 * Tests business logic, validation, and tenant isolation without database.
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TaskMapper taskMapper;

    @InjectMocks
    private TaskService taskService;

    private static final String TENANT_ID = "test-tenant-123";
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should create task successfully with valid data")
    void createTask_WithValidData_ShouldSucceed() {
        // Given
        TaskRequest request = createValidTaskRequest();
        Project project = createProject(TENANT_ID);
        User assignee = createUser(TENANT_ID);
        Task task = createTask();
        TaskResponse expectedResponse = createTaskResponse();

        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.of(project));
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(assignee));
        when(taskMapper.toEntity(request)).thenReturn(task);
        when(taskRepository.save(any(Task.class))).thenReturn(task);
        when(taskMapper.toResponse(task)).thenReturn(expectedResponse);

        // When
        TaskResponse result = taskService.createTask(request);

        // Then
        assertNotNull(result);
        assertEquals(expectedResponse, result);
        verify(projectRepository).findByIdAndTenantId(PROJECT_ID, TENANT_ID);
        verify(userRepository).findById(USER_ID);
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    @DisplayName("Should throw exception when tenant context is missing")
    void createTask_WithoutTenantContext_ShouldThrowException() {
        // Given
        TenantContext.clear();
        TaskRequest request = createValidTaskRequest();

        // When & Then
        assertThrows(SecurityException.class, () -> taskService.createTask(request));
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when project belongs to different tenant")
    void createTask_WithCrossTenantProject_ShouldThrowException() {
        // Given
        TaskRequest request = createValidTaskRequest();
        Project project = createProject("different-tenant");

        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.of(project));

        // When & Then
        assertThrows(AccessDeniedException.class, () -> taskService.createTask(request));
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when assignee belongs to different tenant")
    void createTask_WithCrossTenantAssignee_ShouldThrowException() {
        // Given
        TaskRequest request = createValidTaskRequest();
        Project project = createProject(TENANT_ID);
        User assignee = createUser("different-tenant");
        Task task = createTask(); // Add task mock

        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.of(project));
        when(taskMapper.toEntity(request)).thenReturn(task); // Add mapper mock
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(assignee));

        // When & Then
        assertThrows(AccessDeniedException.class, () -> taskService.createTask(request));
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update task status with valid transition")
    void updateTaskStatus_WithValidTransition_ShouldSucceed() {
        // Given
        Task task = createTask();
        task.setStatus(TaskStatus.TODO);
        TaskResponse expectedResponse = createTaskResponse();

        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID))
                .thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);
        when(taskMapper.toResponse(task)).thenReturn(expectedResponse);

        // When
        TaskResponse result = taskService.updateTaskStatus(TASK_ID, TaskStatus.IN_PROGRESS);

        // Then
        assertNotNull(result);
        assertEquals(TaskStatus.IN_PROGRESS, task.getStatus());
        verify(taskRepository).save(task);
    }

    @Test
    @DisplayName("Should throw exception when status transition is invalid")
    void updateTaskStatus_WithInvalidTransition_ShouldThrowException() {
        // Given
        Task task = createTask();
        task.setStatus(TaskStatus.DONE);

        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID))
                .thenReturn(Optional.of(task));

        // When & Then
        assertThrows(InvalidTaskStateException.class, 
            () -> taskService.updateTaskStatus(TASK_ID, TaskStatus.TODO));
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when task not found")
    void getTaskById_WhenNotFound_ShouldThrowException() {
        // Given
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        // When & Then
        assertThrows(TaskNotFoundException.class, 
            () -> taskService.getTaskById(TASK_ID));
    }

    @Test
    @DisplayName("Should assign task to user successfully")
    void assignTask_WithValidUser_ShouldSucceed() {
        // Given
        Task task = createTask();
        User assignee = createUser(TENANT_ID);
        TaskResponse expectedResponse = createTaskResponse();

        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID))
                .thenReturn(Optional.of(task));
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(assignee));
        when(taskRepository.save(task)).thenReturn(task);
        when(taskMapper.toResponse(task)).thenReturn(expectedResponse);

        // When
        TaskResponse result = taskService.assignTask(TASK_ID, USER_ID);

        // Then
        assertNotNull(result);
        assertEquals(assignee, task.getAssignedTo());
        verify(taskRepository).save(task);
    }

    @Test
    @DisplayName("Should unassign task successfully")
    void unassignTask_ShouldSucceed() {
        // Given
        Task task = createTask();
        task.setAssignedTo(createUser(TENANT_ID));
        TaskResponse expectedResponse = createTaskResponse();

        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID))
                .thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);
        when(taskMapper.toResponse(task)).thenReturn(expectedResponse);

        // When
        TaskResponse result = taskService.unassignTask(TASK_ID);

        // Then
        assertNotNull(result);
        assertNull(task.getAssignedTo());
        verify(taskRepository).save(task);
    }

    @Test
    @DisplayName("Should soft delete task successfully")
    void deleteTask_ShouldSucceed() {
        // Given
        Task task = createTask();

        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID))
                .thenReturn(Optional.of(task));

        // When
        taskService.deleteTask(TASK_ID);

        // Then
        verify(taskRepository).delete(task);
    }

    // Helper methods
    private TaskRequest createValidTaskRequest() {
        TaskRequest request = new TaskRequest();
        request.setProjectId(PROJECT_ID);
        request.setTitle("Test Task");
        request.setDescription("Test Description");
        request.setAssignedToId(USER_ID);
        request.setPriority(TaskPriority.MEDIUM);
        request.setStatus(TaskStatus.TODO);
        request.setEstimatedHours(5);
        request.setDueDate(LocalDateTime.now().plusDays(7));
        return request;
    }

    private Project createProject(String tenantId) {
        Project project = new Project();
        project.setTenantId(tenantId);
        project.setTitle("Test Project");
        project.setStatus(ProjectStatus.IN_PROGRESS);
        // ID will be set by repository when saved
        return project;
    }

    private User createUser(String tenantId) {
        User user = User.builder()
                .tenantId(tenantId)
                .email("test@example.com")
                .password("password")
                .fullName("Test User")
                .role(Role.CLIENT)
                .build();
        // ID will be set by repository when saved
        return user;
    }

    private Task createTask() {
        Task task = new Task();
        task.setId(TASK_ID);
        task.setTenantId(TENANT_ID);
        task.setTitle("Test Task");
        task.setStatus(TaskStatus.TODO);
        task.setPriority(TaskPriority.MEDIUM);
        return task;
    }

    private TaskResponse createTaskResponse() {
        TaskResponse response = new TaskResponse();
        response.setId(TASK_ID);
        response.setTitle("Test Task");
        response.setStatus(TaskStatus.TODO);
        response.setPriority(TaskPriority.MEDIUM);
        return response;
    }
}
