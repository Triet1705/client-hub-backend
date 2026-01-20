package com.clienthub.core.service;

import com.clienthub.common.context.TenantContext;
import com.clienthub.core.aop.LogAudit;
import com.clienthub.core.domain.entity.Project;
import com.clienthub.core.domain.entity.Task;
import com.clienthub.core.domain.entity.User;
import com.clienthub.core.domain.enums.AuditAction;
import com.clienthub.core.domain.enums.Role;
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

    private String getValidatedTenantId() {
        String tenantId = TenantContext.getTenantId();
        if (!StringUtils.hasText(tenantId)) {
            logger.error("TenantContext is null or empty - Security breach attempt detected");
            throw new IllegalStateException("Tenant context not set. Authentication required.");
        }
        return tenantId;
    }

    private void validateUserTenant(User user, String expectedTenantId) {
        if (!expectedTenantId.equals(user.getTenantId())) {
            throw new AccessDeniedException("Cannot assign users from different tenants");
        }
    }

    private void validateProjectTenant(Project project, String expectedTenantId) {
        if (!expectedTenantId.equals(project.getTenantId())) {
            throw new AccessDeniedException("Cannot create tasks in projects from different tenants");
        }
    }

    @LogAudit(action = AuditAction.CREATE, entityType = "TASK", entityId = "#result.id")
    public TaskResponse createTask(TaskRequest request) {
        String tenantId = getValidatedTenantId();

        Project project = projectRepository.findByIdAndTenantId(request.getProjectId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", request.getProjectId()));

        validateProjectTenant(project, tenantId);

        Task task = taskMapper.toEntity(request);
        task.setProject(project);
        task.setTenantId(tenantId);

        if (request.getAssignedToId() != null) {
            User assignee = userRepository.findById(request.getAssignedToId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getAssignedToId()));

            validateUserTenant(assignee, tenantId);
            task.setAssignedTo(assignee);
        }

        Task savedTask = taskRepository.save(task);
        // Removed manual audit logger
        return taskMapper.toResponse(savedTask);
    }

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

    @Transactional(readOnly = true)
    public TaskResponse getTaskById(UUID taskId) {
        String tenantId = getValidatedTenantId();
        Task task = taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new TaskNotFoundException(taskId, tenantId));
        return taskMapper.toResponse(task);
    }

    @LogAudit(action = AuditAction.UPDATE, entityType = "TASK", entityId = "#taskId")
    public TaskResponse updateTask(UUID taskId, TaskRequest request, UUID currentUserId) {
        String tenantId = getValidatedTenantId();
        Task task = taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new TaskNotFoundException(taskId, tenantId));

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));

        boolean isProjectOwner = task.getProject().getOwner().getId().equals(currentUserId);
        boolean isAssignee = task.getAssignedTo() != null && task.getAssignedTo().getId().equals(currentUserId);
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;

        if (!isProjectOwner && !isAssignee && !isAdmin) {
            throw new AccessDeniedException("Only Project Owner or Assignee can update this task.");
        }

        if (request.getStatus() != null && task.getStatus() != request.getStatus()) {
            validateStatusTransition(task, request.getStatus());
        }

        taskMapper.updateEntityFromRequest(request, task);

        if (request.getProjectId() != null && !task.getProject().getId().equals(request.getProjectId())) {
            Project newProject = projectRepository.findByIdAndTenantId(request.getProjectId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Project", "id", request.getProjectId()));
            validateProjectTenant(newProject, tenantId);
            task.setProject(newProject);
        }

        if (request.getAssignedToId() != null) {
            if (task.getAssignedTo() == null || !task.getAssignedTo().getId().equals(request.getAssignedToId())) {
                User newAssignee = userRepository.findById(request.getAssignedToId())
                        .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getAssignedToId()));
                validateUserTenant(newAssignee, tenantId);
                task.setAssignedTo(newAssignee);
            }
        }

        Task updatedTask = taskRepository.save(task);
        return taskMapper.toResponse(updatedTask);
    }

    @LogAudit(action = AuditAction.UPDATE, entityType = "TASK", entityId = "#taskId")
    public TaskResponse updateTaskStatus(UUID taskId, TaskStatus newStatus) {
        String tenantId = getValidatedTenantId();
        Task task = taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new TaskNotFoundException(taskId, tenantId));

        validateStatusTransition(task, newStatus);
        task.setStatus(newStatus);

        Task updatedTask = taskRepository.save(task);
        return taskMapper.toResponse(updatedTask);
    }

    private void validateStatusTransition(Task task, TaskStatus newStatus) {
        if (!TaskStatusTransition.isTransitionAllowed(task.getStatus(), newStatus)) {
            throw new InvalidTaskStateException(task.getId(), task.getStatus(), newStatus);
        }
    }

    @LogAudit(action = AuditAction.DELETE, entityType = "TASK", entityId = "#taskId")
    public void deleteTask(UUID taskId) {
        String tenantId = getValidatedTenantId();
        Task task = taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new TaskNotFoundException(taskId, tenantId));
        taskRepository.delete(task);
    }

    @LogAudit(action = AuditAction.UPDATE, entityType = "TASK", entityId = "#taskId")
    public TaskResponse assignTask(UUID taskId, UUID userId) {
        String tenantId = getValidatedTenantId();
        Task task = taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new TaskNotFoundException(taskId, tenantId));

        User assignee = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        validateUserTenant(assignee, tenantId);

        task.setAssignedTo(assignee);
        Task updatedTask = taskRepository.save(task);
        return taskMapper.toResponse(updatedTask);
    }

    @LogAudit(action = AuditAction.UPDATE, entityType = "TASK", entityId = "#taskId")
    public TaskResponse unassignTask(UUID taskId) {
        String tenantId = getValidatedTenantId();
        Task task = taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new TaskNotFoundException(taskId, tenantId));

        task.setAssignedTo(null);
        Task updatedTask = taskRepository.save(task);
        return taskMapper.toResponse(updatedTask);
    }
}