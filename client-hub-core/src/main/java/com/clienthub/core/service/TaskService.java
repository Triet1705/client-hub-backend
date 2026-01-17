package com.clienthub.core.service;

import com.clienthub.common.context.TenantContext;
import com.clienthub.core.domain.entity.Project;
import com.clienthub.core.domain.entity.Task;
import com.clienthub.core.domain.entity.User;
import com.clienthub.core.domain.enums.TaskStatus;
import com.clienthub.core.dto.task.TaskRequest;
import com.clienthub.core.dto.task.TaskResponse;
import com.clienthub.core.exception.InvalidTaskStateException;
import com.clienthub.core.exception.ResourceNotFoundException;
import com.clienthub.core.exception.TaskNotFoundException;
import com.clienthub.core.mapper.TaskMapper;
import com.clienthub.core.repository.ProjectRepository;
import com.clienthub.core.repository.TaskRepository;
import com.clienthub.core.repository.UserRepository;
import com.clienthub.core.validation.TaskStatusTransition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Service layer for Task management with tenant isolation and business logic validation.
 * Implements multi-tenancy security, state machine validation, and audit logging.
 */
@Service
@Transactional
public class TaskService {

    private static final Logger logger = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TaskMapper taskMapper;

    public TaskService(TaskRepository taskRepository,
                       ProjectRepository projectRepository,
                       UserRepository userRepository,
                       TaskMapper taskMapper) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.taskMapper = taskMapper;
    }

    /**
     * Validates and retrieves the current tenant ID from security context.
     * 
     * @return validated tenant ID
     * @throws IllegalStateException if tenant context is not set
     */
    private String getValidatedTenantId() {
        String tenantId = TenantContext.getTenantId();
        if (!StringUtils.hasText(tenantId)) {
            logger.error("TenantContext is null or empty - Security breach attempt detected");
            throw new IllegalStateException("Tenant context not set. Authentication required.");
        }
        return tenantId;
    }

    /**
     * Validates that a user belongs to the expected tenant.
     * 
     * @param user the user to validate
     * @param expectedTenantId the expected tenant ID
     * @throws AccessDeniedException if user belongs to a different tenant
     */
    private void validateUserTenant(User user, String expectedTenantId) {
        if (!expectedTenantId.equals(user.getTenantId())) {
            logger.warn("SECURITY: Attempt to assign user {} from tenant {} to task in tenant {}",
                    user.getId(), user.getTenantId(), expectedTenantId);
            throw new AccessDeniedException("Cannot assign users from different tenants");
        }
    }

    /**
     * Validates that a project belongs to the expected tenant.
     * 
     * @param project the project to validate
     * @param expectedTenantId the expected tenant ID
     * @throws AccessDeniedException if project belongs to a different tenant
     */
    private void validateProjectTenant(Project project, String expectedTenantId) {
        if (!expectedTenantId.equals(project.getTenantId())) {
            logger.warn("SECURITY: Attempt to create task in project {} from tenant {} using tenant {}",
                    project.getId(), project.getTenantId(), expectedTenantId);
            throw new AccessDeniedException("Cannot create tasks in projects from different tenants");
        }
    }

    /**
     * Creates a new task with multi-tenancy isolation.
     * 
     * @param request the task creation request
     * @return the created task response
     * @throws ResourceNotFoundException if project or assigned user not found
     * @throws AccessDeniedException if tenant mismatch detected
     */
    public TaskResponse createTask(TaskRequest request) {
        String tenantId = getValidatedTenantId();

        // Fetch and validate project with tenant isolation
        Project project = projectRepository.findByIdAndTenantId(request.getProjectId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", request.getProjectId()));

        validateProjectTenant(project, tenantId);

        // Convert DTO to entity
        Task task = taskMapper.toEntity(request);
        task.setProject(project);
        task.setTenantId(tenantId);

        // Handle optional assignee
        if (request.getAssignedToId() != null) {
            User assignee = userRepository.findById(request.getAssignedToId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getAssignedToId()));
            
            validateUserTenant(assignee, tenantId);
            task.setAssignedTo(assignee);
        }

        Task savedTask = taskRepository.save(task);

        logger.info("[AUDIT] Task created: id={}, title='{}', project={}, tenant={}",
                savedTask.getId(), savedTask.getTitle(), project.getId(), tenantId);

        return taskMapper.toResponse(savedTask);
    }

    /**
     * Retrieves all tasks for the current tenant with optional filters.
     * 
     * @param projectId optional project filter
     * @param status optional status filter
     * @param assignedToId optional assignee filter
     * @param pageable pagination information
     * @return page of task responses
     */
    @Transactional(readOnly = true)
    public Page<TaskResponse> getTasks(UUID projectId, TaskStatus status, UUID assignedToId, Pageable pageable) {
        String tenantId = getValidatedTenantId();

        Page<Task> tasks;

        if (projectId != null && status != null) {
            tasks = taskRepository.findByProjectIdAndStatusAndTenantId(projectId, status, tenantId, pageable);
        } else if (projectId != null) {
            tasks = taskRepository.findByProjectIdAndTenantId(projectId, tenantId, pageable);
        } else if (assignedToId != null) {
            tasks = taskRepository.findByAssignedToIdAndTenantId(assignedToId, tenantId, pageable);
        } else {
            tasks = taskRepository.findAllByTenantId(tenantId, pageable);
        }

        return tasks.map(taskMapper::toResponse);
    }

    /**
     * Retrieves a single task by ID with tenant isolation.
     * 
     * @param taskId the task ID
     * @return the task response
     * @throws TaskNotFoundException if task not found or belongs to different tenant
     */
    @Transactional(readOnly = true)
    public TaskResponse getTaskById(UUID taskId) {
        String tenantId = getValidatedTenantId();

        Task task = taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new TaskNotFoundException(taskId, tenantId));

        return taskMapper.toResponse(task);
    }

    /**
     * Updates an existing task with validation and tenant isolation.
     * 
     * @param taskId the task ID to update
     * @param request the update request
     * @return the updated task response
     * @throws TaskNotFoundException if task not found
     * @throws InvalidTaskStateException if status transition is invalid
     */
    public TaskResponse updateTask(UUID taskId, TaskRequest request) {
        String tenantId = getValidatedTenantId();

        Task task = taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new TaskNotFoundException(taskId, tenantId));

        // Validate status transition if status is being changed
        if (request.getStatus() != null && task.getStatus() != request.getStatus()) {
            validateStatusTransition(task, request.getStatus());
        }

        // Update task fields
        taskMapper.updateEntityFromRequest(request, task);

        // Handle project change (must be in same tenant)
        if (request.getProjectId() != null && !task.getProject().getId().equals(request.getProjectId())) {
            Project newProject = projectRepository.findByIdAndTenantId(request.getProjectId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Project", "id", request.getProjectId()));
            
            validateProjectTenant(newProject, tenantId);
            task.setProject(newProject);
        }

        // Handle assignee change (must be in same tenant)
        if (request.getAssignedToId() != null) {
            if (task.getAssignedTo() == null || !task.getAssignedTo().getId().equals(request.getAssignedToId())) {
                User newAssignee = userRepository.findById(request.getAssignedToId())
                        .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getAssignedToId()));
                
                validateUserTenant(newAssignee, tenantId);
                task.setAssignedTo(newAssignee);
            }
        }

        Task updatedTask = taskRepository.save(task);

        logger.info("[AUDIT] Task updated: id={}, title='{}', tenant={}",
                updatedTask.getId(), updatedTask.getTitle(), tenantId);

        return taskMapper.toResponse(updatedTask);
    }

    /**
     * Updates only the status of a task with state machine validation.
     * 
     * @param taskId the task ID
     * @param newStatus the new status
     * @return the updated task response
     * @throws InvalidTaskStateException if transition is not allowed
     */
    public TaskResponse updateTaskStatus(UUID taskId, TaskStatus newStatus) {
        String tenantId = getValidatedTenantId();

        Task task = taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new TaskNotFoundException(taskId, tenantId));

        validateStatusTransition(task, newStatus);

        TaskStatus oldStatus = task.getStatus();
        task.setStatus(newStatus);

        Task updatedTask = taskRepository.save(task);

        logger.info("[AUDIT] Task status changed: id={}, {} -> {}, tenant={}",
                taskId, oldStatus, newStatus, tenantId);

        return taskMapper.toResponse(updatedTask);
    }

    /**
     * Validates task status transition using the state machine.
     * 
     * @param task the task to validate
     * @param newStatus the requested new status
     * @throws InvalidTaskStateException if transition is not allowed
     */
    private void validateStatusTransition(Task task, TaskStatus newStatus) {
        if (!TaskStatusTransition.isTransitionAllowed(task.getStatus(), newStatus)) {
            logger.warn("Invalid task status transition: task={}, {} -> {}",
                    task.getId(), task.getStatus(), newStatus);
            throw new InvalidTaskStateException(task.getId(), task.getStatus(), newStatus);
        }
    }

    /**
     * Soft deletes a task (sets isDeleted flag).
     * 
     * @param taskId the task ID to delete
     * @throws TaskNotFoundException if task not found
     */
    public void deleteTask(UUID taskId) {
        String tenantId = getValidatedTenantId();

        Task task = taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new TaskNotFoundException(taskId, tenantId));

        // Soft delete is handled by @SQLDelete annotation on Task entity
        taskRepository.delete(task);

        logger.info("[AUDIT] Task soft-deleted: id={}, title='{}', tenant={}",
                taskId, task.getTitle(), tenantId);
    }

    /**
     * Assigns a task to a user with tenant validation.
     * 
     * @param taskId the task ID
     * @param userId the user ID to assign to
     * @return the updated task response
     */
    public TaskResponse assignTask(UUID taskId, UUID userId) {
        String tenantId = getValidatedTenantId();

        Task task = taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new TaskNotFoundException(taskId, tenantId));

        User assignee = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        validateUserTenant(assignee, tenantId);

        task.setAssignedTo(assignee);
        Task updatedTask = taskRepository.save(task);

        logger.info("[AUDIT] Task assigned: id={}, assignee={}, tenant={}",
                taskId, userId, tenantId);

        return taskMapper.toResponse(updatedTask);
    }

    /**
     * Unassigns a task (removes assignee).
     * 
     * @param taskId the task ID
     * @return the updated task response
     */
    public TaskResponse unassignTask(UUID taskId) {
        String tenantId = getValidatedTenantId();

        Task task = taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new TaskNotFoundException(taskId, tenantId));

        task.setAssignedTo(null);
        Task updatedTask = taskRepository.save(task);

        logger.info("[AUDIT] Task unassigned: id={}, tenant={}",
                taskId, tenantId);

        return taskMapper.toResponse(updatedTask);
    }
}
