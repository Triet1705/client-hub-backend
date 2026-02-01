package com.clienthub.application.service;

import com.clienthub.common.service.TenantAwareService;
import com.clienthub.domain.entity.Project;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.ProjectStatus;
import com.clienthub.application.dto.project.ProjectRequest;
import com.clienthub.application.dto.project.ProjectResponse;
import com.clienthub.application.exception.ResourceNotFoundException;
import com.clienthub.application.mapper.ProjectMapper;
import com.clienthub.domain.repository.ProjectRepository;
import com.clienthub.domain.repository.UserRepository;
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
public class ProjectService extends TenantAwareService {

    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectMapper projectMapper;

    public ProjectService(ProjectRepository projectRepository, UserRepository userRepository, ProjectMapper projectMapper) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.projectMapper = projectMapper;
    }

    private void validateUserTenant(User user, String expectedTenantId) {
        if (!expectedTenantId.equals(user.getTenantId())) {
            logger.warn("SECURITY: Attempt to assign user {} from tenant {} to project in tenant {}",
                    user.getId(), user.getTenantId(), expectedTenantId);
            throw new AccessDeniedException("Cannot assign users from different tenants");
        }
    }

    private void validateStatusTransition(ProjectStatus currentStatus, ProjectStatus newStatus) {
        if (currentStatus == newStatus) {
            return;
        }

        boolean isValidTransition = switch (currentStatus) {
            case PLANNING -> newStatus == ProjectStatus.IN_PROGRESS || newStatus == ProjectStatus.CANCELLED;
            case IN_PROGRESS -> newStatus == ProjectStatus.ON_HOLD || 
                               newStatus == ProjectStatus.COMPLETED || 
                               newStatus == ProjectStatus.CANCELLED;
            case ON_HOLD -> newStatus == ProjectStatus.IN_PROGRESS || newStatus == ProjectStatus.CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };

        if (!isValidTransition) {
            logger.warn("Invalid status transition: {} -> {}", currentStatus, newStatus);
            throw new IllegalStateException(
                String.format("Invalid status transition from %s to %s", currentStatus, newStatus)
            );
        }
    }

    public ProjectResponse createProject(ProjectRequest request, UUID userId) {
        String tenantId = getCurrentTenantId();

        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        validateUserTenant(owner, tenantId);

        Project project = projectMapper.toEntity(request);
        project.setOwner(owner);
        project.setStatus(ProjectStatus.PLANNING);

        Project savedProject = projectRepository.save(project);

        logger.info("[AUDIT] Project created: id={}, title='{}', owner={}, tenant={}",
                savedProject.getId(), savedProject.getTitle(), userId, tenantId);

        return projectMapper.toResponse(savedProject);
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponse> getProjects(ProjectStatus status, Pageable pageable) {
        String tenantId = getCurrentTenantId(); // MEDIUM 6: Null safety

        Page<Project> projects;
        if (status != null) {
            projects = projectRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        } else {
            projects = projectRepository.findAllByTenantId(tenantId, pageable);
        }

        return projects.map(projectMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(UUID projectId) {
        String tenantId = getCurrentTenantId();

        Project project = projectRepository.findByIdAndTenantId(projectId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        return projectMapper.toResponse(project);
    }

    public ProjectResponse updateProject(UUID projectId, ProjectRequest request, UUID currentUserId) {
        String tenantId = getCurrentTenantId();

        Project project = projectRepository.findByIdAndTenantId(projectId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        if (!project.getOwner().getId().equals(currentUserId)) {
            logger.warn("[SECURITY] User {} attempted to update project {} owned by {}",
                    currentUserId, projectId, project.getOwner().getId());
            throw new AccessDeniedException("You can only update your own projects");
        }

        ProjectStatus oldStatus = project.getStatus();
        if (request.getStatus() != null && request.getStatus() != oldStatus) {
            validateStatusTransition(oldStatus, request.getStatus());
        }

        projectMapper.updateEntityFromRequest(request, project);
        Project updatedProject = projectRepository.save(project);

        logger.info("[AUDIT] Project updated: id={}, user={}, statusChange={}->{}",
                projectId, currentUserId, oldStatus, updatedProject.getStatus());

        return projectMapper.toResponse(updatedProject);
    }

    public void deleteProject(UUID projectId, UUID currentUserId) {
        String tenantId = getCurrentTenantId();

        Project project = projectRepository.findByIdAndTenantId(projectId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        if (!project.getOwner().getId().equals(currentUserId)) {
            logger.warn("[SECURITY] User {} attempted to delete project {} owned by {}",
                    currentUserId, projectId, project.getOwner().getId());
            throw new AccessDeniedException("You can only delete your own projects");
        }

        logger.info("[AUDIT] Deleting project: id={}, title='{}', user={} (Note: Related tasks will cascade delete)",
                projectId, project.getTitle(), currentUserId);

        projectRepository.deleteById(projectId);
    }
}
