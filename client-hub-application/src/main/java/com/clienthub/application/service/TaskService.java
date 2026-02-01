package com.clienthub.application.service;

import com.clienthub.common.service.TenantAwareService;
import com.clienthub.application.aop.LogAudit;
import com.clienthub.domain.entity.Project;
import com.clienthub.domain.entity.Task;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.AuditAction;
import com.clienthub.domain.enums.Role;
import com.clienthub.domain.enums.TaskStatus;
import com.clienthub.application.dto.task.TaskRequest;
import com.clienthub.application.dto.task.TaskResponse;
import com.clienthub.application.exception.InvalidTaskStateException;
import com.clienthub.application.exception.ResourceNotFoundException;
import com.clienthub.application.exception.TaskNotFoundException;
import com.clienthub.application.mapper.TaskMapper;
import com.clienthub.domain.repository.ProjectRepository;
import com.clienthub.domain.repository.TaskRepository;
import com.clienthub.domain.repository.UserRepository;
import com.clienthub.application.validation.TaskStatusTransition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class TaskService extends TenantAwareService {

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
        String tenantId = getCurrentTenantId();

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
        String tenantId = getCurrentTenantId();
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
        String tenantId = getCurrentTenantId();
        Task task = taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new TaskNotFoundException(taskId, tenantId));
        return taskMapper.toResponse(task);
    }

    @LogAudit(action = AuditAction.UPDATE, entityType = "TASK", entityId = "#taskId")
    public TaskResponse updateTask(UUID taskId, TaskRequest request, UUID currentUserId) {
        String tenantId = getCurrentTenantId();
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
        String tenantId = getCurrentTenantId();
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
        String tenantId = getCurrentTenantId();
        Task task = taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new TaskNotFoundException(taskId, tenantId));
        taskRepository.delete(task);
    }

    @LogAudit(action = AuditAction.UPDATE, entityType = "TASK", entityId = "#taskId")
    public TaskResponse assignTask(UUID taskId, UUID userId) {
        String tenantId = getCurrentTenantId();
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
        String tenantId = getCurrentTenantId();
        Task task = taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new TaskNotFoundException(taskId, tenantId));

        task.setAssignedTo(null);
        Task updatedTask = taskRepository.save(task);
        return taskMapper.toResponse(updatedTask);
    }
}
