package com.clienthub.application.service;

import com.clienthub.common.service.TenantAwareService;
import com.clienthub.domain.entity.Project;
import com.clienthub.domain.entity.ProjectMember;
import com.clienthub.domain.entity.ProjectMemberId;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.ProjectStatus;
import com.clienthub.domain.enums.Role;
import com.clienthub.application.dto.project.ProjectMemberResponse;
import com.clienthub.application.dto.project.ProjectFreelancerSearchResponse;
import com.clienthub.application.dto.project.ProjectRequest;
import com.clienthub.application.dto.project.ProjectResponse;
import com.clienthub.application.exception.ResourceNotFoundException;
import com.clienthub.application.mapper.ProjectMapper;
import com.clienthub.domain.repository.ProjectMemberRepository;
import com.clienthub.domain.repository.ProjectRepository;
import com.clienthub.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class ProjectService extends TenantAwareService {

    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final ProjectMapper projectMapper;

    public ProjectService(ProjectRepository projectRepository,
                          ProjectMemberRepository projectMemberRepository,
                          UserRepository userRepository,
                          ProjectMapper projectMapper) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
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
        return getProjects(status, pageable, null, null);
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponse> getProjects(ProjectStatus status, Pageable pageable, UUID currentUserId, Role callerRole) {
        String tenantId = getCurrentTenantId(); // MEDIUM 6: Null safety

        Page<Project> projects;
        if (callerRole == Role.FREELANCER && currentUserId != null) {
            if (status != null) {
                projects = projectRepository.findMemberProjectsByUserIdAndTenantIdAndStatus(
                        currentUserId, tenantId, status, pageable);
            } else {
                projects = projectRepository.findMemberProjectsByUserIdAndTenantId(
                        currentUserId, tenantId, pageable);
            }
        } else {
            if (status != null) {
                projects = projectRepository.findByTenantIdAndStatus(tenantId, status, pageable);
            } else {
                projects = projectRepository.findAllByTenantId(tenantId, pageable);
            }
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

    public ProjectResponse updateProject(UUID projectId, ProjectRequest request, UUID currentUserId, boolean isAdmin) {
        String tenantId = getCurrentTenantId();

        Project project = projectRepository.findByIdAndTenantId(projectId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        if (!isAdmin && !project.getOwner().getId().equals(currentUserId)) {
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

    public void deleteProject(UUID projectId, UUID currentUserId, boolean isAdmin) {
        String tenantId = getCurrentTenantId();

        Project project = projectRepository.findByIdAndTenantId(projectId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        if (!isAdmin && !project.getOwner().getId().equals(currentUserId)) {
            logger.warn("[SECURITY] User {} attempted to delete project {} owned by {}",
                    currentUserId, projectId, project.getOwner().getId());
            throw new AccessDeniedException("You can only delete your own projects");
        }

        logger.info("[AUDIT] Deleting project: id={}, title='{}', user={} (Note: Related tasks will cascade delete)",
                projectId, project.getTitle(), currentUserId);

        projectRepository.deleteById(projectId);
    }

    public ProjectMemberResponse addMember(UUID projectId, UUID memberUserId, UUID currentUserId, boolean isAdmin) {
        String tenantId = getCurrentTenantId();

        Project project = projectRepository.findByIdAndTenantId(projectId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
        validateProjectOwnerAccess(project, currentUserId, isAdmin);

        User memberUser = userRepository.findById(memberUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", memberUserId));

        validateUserTenant(memberUser, tenantId);
        if (memberUser.getRole() != Role.FREELANCER) {
            throw new AccessDeniedException("Only FREELANCER users can be added as project members");
        }

        boolean alreadyMember = projectMemberRepository.existsByIdProjectIdAndIdUserIdAndTenantId(
                projectId, memberUserId, tenantId);
        if (alreadyMember) {
            throw new IllegalStateException("User is already a project member");
        }

        ProjectMember membership = new ProjectMember();
        membership.setId(new ProjectMemberId(projectId, memberUserId));
        membership.setProject(project);
        membership.setUser(memberUser);
        membership.setTenantId(tenantId);

        ProjectMember savedMembership = projectMemberRepository.save(membership);
        logger.info("[AUDIT] Project member added: projectId={}, memberId={}, actor={}, tenant={}",
                projectId, memberUserId, currentUserId, tenantId);

        return toProjectMemberResponse(savedMembership);
    }

    public void removeMember(UUID projectId, UUID memberUserId, UUID currentUserId, boolean isAdmin) {
        String tenantId = getCurrentTenantId();

        Project project = projectRepository.findByIdAndTenantId(projectId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
        validateProjectOwnerAccess(project, currentUserId, isAdmin);

        ProjectMember membership = projectMemberRepository
                .findByIdProjectIdAndIdUserIdAndTenantId(projectId, memberUserId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ProjectMember", "projectId+userId", projectId + ":" + memberUserId));

        projectMemberRepository.delete(membership);
        logger.info("[AUDIT] Project member removed: projectId={}, memberId={}, actor={}, tenant={}",
                projectId, memberUserId, currentUserId, tenantId);
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> getProjectMembers(UUID projectId, UUID currentUserId, Role callerRole) {
        String tenantId = getCurrentTenantId();

        Project project = projectRepository.findByIdAndTenantId(projectId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        boolean isAdmin = callerRole == Role.ADMIN;
        boolean isOwner = project.getOwner().getId().equals(currentUserId);
        boolean isMember = projectMemberRepository.existsByIdProjectIdAndIdUserIdAndTenantId(
                projectId, currentUserId, tenantId);

        if (!isAdmin && !isOwner && !isMember) {
            throw new AccessDeniedException("You are not allowed to view members of this project");
        }

        return projectMemberRepository.findByIdProjectIdAndTenantId(projectId, tenantId)
                .stream()
                .map(this::toProjectMemberResponse)
                .toList();
    }

            @Transactional(readOnly = true)
            public List<ProjectFreelancerSearchResponse> searchAvailableFreelancers(
                UUID projectId,
                String keyword,
                UUID currentUserId,
                boolean isAdmin
            ) {
            String tenantId = getCurrentTenantId();

            Project project = projectRepository.findByIdAndTenantId(projectId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
            validateProjectOwnerAccess(project, currentUserId, isAdmin);

            String normalizedKeyword = keyword == null ? null : keyword.trim();
            if (normalizedKeyword != null && normalizedKeyword.isBlank()) {
                normalizedKeyword = null;
            }

            Page<User> candidates = userRepository.searchActiveUsersByTenantIdAndRoleAndKeyword(
                tenantId,
                Role.FREELANCER,
                normalizedKeyword,
                PageRequest.of(0, 20)
            );

            Set<UUID> existingMemberIds = projectMemberRepository.findByIdProjectIdAndTenantId(projectId, tenantId)
                .stream()
                .map(member -> member.getUser().getId())
                .collect(java.util.stream.Collectors.toSet());

            return candidates.getContent().stream()
                .filter(user -> !existingMemberIds.contains(user.getId()))
                .map(this::toFreelancerSearchResponse)
                .toList();
            }

    private void validateProjectOwnerAccess(Project project, UUID currentUserId, boolean isAdmin) {
        if (!isAdmin && !project.getOwner().getId().equals(currentUserId)) {
            throw new AccessDeniedException("You can only manage members on your own projects");
        }
    }

    private ProjectMemberResponse toProjectMemberResponse(ProjectMember projectMember) {
        ProjectMemberResponse response = new ProjectMemberResponse();
        response.setUserId(projectMember.getUser().getId());
        response.setEmail(projectMember.getUser().getEmail());
        response.setFullName(projectMember.getUser().getFullName());
        response.setRole(projectMember.getUser().getRole().name());
        response.setAddedAt(projectMember.getAddedAt());
        return response;
    }

    private ProjectFreelancerSearchResponse toFreelancerSearchResponse(User user) {
        ProjectFreelancerSearchResponse response = new ProjectFreelancerSearchResponse();
        response.setUserId(user.getId());
        response.setEmail(user.getEmail());
        response.setFullName(user.getFullName());
        response.setRole(user.getRole().name());
        return response;
    }
}
