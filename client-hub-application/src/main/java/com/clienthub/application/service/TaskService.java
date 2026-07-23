package com.clienthub.application.service;

import com.clienthub.application.aop.LogAudit;
import com.clienthub.application.dto.task.TaskRequest;
import com.clienthub.application.dto.task.TaskResponse;
import com.clienthub.application.dto.task.TaskSummaryResponse;
import com.clienthub.application.exception.InvalidTaskStateException;
import com.clienthub.application.exception.ResourceNotFoundException;
import com.clienthub.application.exception.TaskNotFoundException;
import com.clienthub.application.mapper.TaskMapper;
import com.clienthub.application.validation.TaskStatusTransition;
import com.clienthub.common.service.TenantAwareService;
import com.clienthub.domain.entity.Project;
import com.clienthub.domain.entity.Task;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.AuditAction;
import com.clienthub.domain.enums.Role;
import com.clienthub.domain.enums.TaskPriority;
import com.clienthub.domain.enums.TaskStatus;
import com.clienthub.domain.repository.ProjectMemberRepository;
import com.clienthub.domain.repository.ProjectRepository;
import com.clienthub.domain.repository.TaskRepository;
import com.clienthub.domain.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class TaskService extends TenantAwareService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final TaskMapper taskMapper;
    private final NotificationProducerService notificationProducerService;

    public TaskService(TaskRepository taskRepository,
                       ProjectRepository projectRepository,
                       ProjectMemberRepository projectMemberRepository,
                       UserRepository userRepository,
                       TaskMapper taskMapper,
                       NotificationProducerService notificationProducerService) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.userRepository = userRepository;
        this.taskMapper = taskMapper;
        this.notificationProducerService = notificationProducerService;
    }

    @LogAudit(action = AuditAction.CREATE, entityType = "TASK", entityId = "#result.id")
    public TaskResponse createTask(TaskRequest request, UUID currentUserId) {
        String tenantId = getCurrentTenantId();
        User actor = loadActor(currentUserId, tenantId);
        Project project = loadProject(request.getProjectId(), tenantId);
        boolean isMemberFreelancer = isExplicitFreelancerMember(project.getId(), actor, tenantId);
        TaskAccessPolicy.requireProjectCreateAccess(project, actor, isMemberFreelancer);

        Task task = taskMapper.toEntity(request);
        task.setProject(project);
        task.setTenantId(tenantId);

        if (request.getAssignedToId() != null) {
            User assignee = loadEligibleAssignee(request.getAssignedToId(), project.getId(), tenantId);
            if (actor.getRole() == Role.FREELANCER && !actor.getId().equals(assignee.getId())) {
                throw new AccessDeniedException("Freelancers may only create tasks assigned to themselves");
            }
            task.setAssignedTo(assignee);
        } else if (actor.getRole() == Role.FREELANCER) {
            throw new AccessDeniedException("Freelancers must assign created tasks to themselves");
        }

        Task savedTask = taskRepository.save(task);
        return taskMapper.toResponse(savedTask);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> getTasks(UUID projectId,
                                       TaskStatus status,
                                       TaskPriority priority,
                                       UUID assignedToId,
                                       UUID currentUserId,
                                       Pageable pageable) {
        String tenantId = getCurrentTenantId();
        User actor = loadActor(currentUserId, tenantId);
        Page<Task> tasks = switch (actor.getRole()) {
            case CLIENT -> taskRepository.findVisibleToClient(
                    tenantId, actor.getId(), projectId, status, priority, assignedToId, pageable);
            case FREELANCER -> taskRepository.findVisibleToFreelancer(
                    tenantId, actor.getId(), projectId, status, priority, pageable);
            case ADMIN -> taskRepository.findVisibleToAdministrator(
                    tenantId, projectId, status, priority, assignedToId, pageable);
        };
        return tasks.map(taskMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public TaskResponse getTaskById(UUID taskId, UUID currentUserId) {
        String tenantId = getCurrentTenantId();
        Task task = loadTask(taskId, tenantId);
        User actor = loadActor(currentUserId, tenantId);
        TaskAccessPolicy.requireReadOrUpdateAccess(task, actor);
        return taskMapper.toResponse(task);
    }

    @LogAudit(action = AuditAction.UPDATE, entityType = "TASK", entityId = "#taskId")
    public TaskResponse updateTask(UUID taskId, TaskRequest request, UUID currentUserId) {
        String tenantId = getCurrentTenantId();
        Task task = loadTask(taskId, tenantId);
        User actor = loadActor(currentUserId, tenantId);
        TaskAccessPolicy.requireReadOrUpdateAccess(task, actor);

        Project effectiveProject = task.getProject();
        boolean movingProject = request.getProjectId() != null
                && !effectiveProject.getId().equals(request.getProjectId());
        if (movingProject) {
            if (actor.getRole() == Role.FREELANCER) {
                throw new AccessDeniedException("Freelancers cannot move tasks between projects");
            }
            effectiveProject = loadProject(request.getProjectId(), tenantId);
            TaskAccessPolicy.requireProjectCreateAccess(effectiveProject, actor, false);
        }

        User effectiveAssignee = task.getAssignedTo();
        boolean changingAssignee = request.getAssignedToId() != null
                && (effectiveAssignee == null
                || !effectiveAssignee.getId().equals(request.getAssignedToId()));
        if (changingAssignee && actor.getRole() == Role.FREELANCER) {
            throw new AccessDeniedException("Freelancers cannot reassign tasks");
        }
        if (request.getAssignedToId() != null) {
            effectiveAssignee = loadEligibleAssignee(
                    request.getAssignedToId(), effectiveProject.getId(), tenantId);
        } else if (movingProject && effectiveAssignee != null) {
            effectiveAssignee = loadEligibleAssignee(
                    effectiveAssignee.getId(), effectiveProject.getId(), tenantId);
        }

        if (request.getStatus() != null && task.getStatus() != request.getStatus()) {
            validateStatusTransition(task, request.getStatus());
        }

        taskMapper.updateEntityFromRequest(request, task);
        task.setProject(effectiveProject);
        task.setAssignedTo(effectiveAssignee);

        Task updatedTask = taskRepository.save(task);
        return taskMapper.toResponse(updatedTask);
    }

    @LogAudit(action = AuditAction.UPDATE, entityType = "TASK", entityId = "#taskId")
    public TaskResponse updateTaskStatus(UUID taskId, TaskStatus newStatus, UUID currentUserId) {
        String tenantId = getCurrentTenantId();
        Task task = loadTask(taskId, tenantId);
        User actor = loadActor(currentUserId, tenantId);
        TaskAccessPolicy.requireReadOrUpdateAccess(task, actor);

        TaskStatus oldStatus = task.getStatus();
        validateStatusTransition(task, newStatus);
        task.setStatus(newStatus);

        Task updatedTask = taskRepository.save(task);
        if (oldStatus != TaskStatus.DONE && newStatus == TaskStatus.DONE) {
            notificationProducerService.notifyTaskCompleted(updatedTask);
        }
        return taskMapper.toResponse(updatedTask);
    }

    @Transactional(readOnly = true)
    public TaskSummaryResponse getTaskSummary(UUID currentUserId) {
        String tenantId = getCurrentTenantId();
        User actor = loadActor(currentUserId, tenantId);
        long todo = countVisibleByStatuses(actor, tenantId, TaskStatus.TODO);
        long inProgress = countVisibleByStatuses(actor, tenantId, TaskStatus.IN_PROGRESS);
        long done = countVisibleByStatuses(actor, tenantId, TaskStatus.DONE);
        return new TaskSummaryResponse(todo, inProgress, done);
    }

    @LogAudit(action = AuditAction.DELETE, entityType = "TASK", entityId = "#taskId")
    public void deleteTask(UUID taskId, UUID currentUserId) {
        String tenantId = getCurrentTenantId();
        Task task = loadTask(taskId, tenantId);
        User actor = loadActor(currentUserId, tenantId);
        TaskAccessPolicy.requireOwnerOrAdmin(
                task, actor, "Only the project owner or Administrator can delete this task");
        taskRepository.delete(task);
    }

    @LogAudit(action = AuditAction.UPDATE, entityType = "TASK", entityId = "#taskId")
    public TaskResponse assignTask(UUID taskId, UUID userId, UUID currentUserId) {
        String tenantId = getCurrentTenantId();
        Task task = loadTask(taskId, tenantId);
        User actor = loadActor(currentUserId, tenantId);
        TaskAccessPolicy.requireOwnerOrAdmin(
                task, actor, "Only the project owner or Administrator can assign this task");
        User assignee = loadEligibleAssignee(userId, task.getProject().getId(), tenantId);

        task.setAssignedTo(assignee);
        Task updatedTask = taskRepository.save(task);
        return taskMapper.toResponse(updatedTask);
    }

    @LogAudit(action = AuditAction.UPDATE, entityType = "TASK", entityId = "#taskId")
    public TaskResponse unassignTask(UUID taskId, UUID currentUserId) {
        String tenantId = getCurrentTenantId();
        Task task = loadTask(taskId, tenantId);
        User actor = loadActor(currentUserId, tenantId);
        TaskAccessPolicy.requireUnassignAccess(task, actor);

        task.setAssignedTo(null);
        Task updatedTask = taskRepository.save(task);
        return taskMapper.toResponse(updatedTask);
    }

    private User loadActor(UUID currentUserId, String tenantId) {
        return userRepository.findByIdAndTenantId(currentUserId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));
    }

    private Project loadProject(UUID projectId, String tenantId) {
        return projectRepository.findByIdAndTenantId(projectId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
    }

    private Task loadTask(UUID taskId, String tenantId) {
        return taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new TaskNotFoundException(taskId, tenantId));
    }

    private User loadEligibleAssignee(UUID userId, UUID projectId, String tenantId) {
        User assignee = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (assignee.getRole() != Role.FREELANCER || !assignee.isActive()) {
            throw new AccessDeniedException("Task assignee must be an active Freelancer");
        }
        if (!projectMemberRepository.existsByIdProjectIdAndIdUserIdAndTenantId(
                projectId, userId, tenantId)) {
            throw new AccessDeniedException("Task assignee must be a member of the project");
        }
        return assignee;
    }

    private boolean isExplicitFreelancerMember(UUID projectId, User actor, String tenantId) {
        return actor.getRole() == Role.FREELANCER
                && projectMemberRepository.existsByIdProjectIdAndIdUserIdAndTenantId(
                projectId, actor.getId(), tenantId);
    }

    private long countVisibleByStatuses(User actor, String tenantId, TaskStatus status) {
        return switch (actor.getRole()) {
            case CLIENT -> taskRepository.countByProjectOwnerIdAndTenantIdAndStatusIn(
                    actor.getId(), tenantId, java.util.List.of(status));
            case FREELANCER -> taskRepository.countByAssignedToIdAndTenantIdAndStatusIn(
                    actor.getId(), tenantId, java.util.List.of(status));
            case ADMIN -> taskRepository.countByTenantIdAndStatusIn(
                    tenantId, java.util.List.of(status));
        };
    }

    private void validateStatusTransition(Task task, TaskStatus newStatus) {
        if (!TaskStatusTransition.isTransitionAllowed(task.getStatus(), newStatus)) {
            throw new InvalidTaskStateException(task.getId(), task.getStatus(), newStatus);
        }
    }
}
