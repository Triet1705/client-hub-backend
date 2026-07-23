package com.clienthub.web.controller;

import com.clienthub.domain.enums.ProjectStatus;
import com.clienthub.domain.enums.Role;
import com.clienthub.application.dto.project.ProjectActivityResponse;
import com.clienthub.application.dto.project.ProjectFileResponse;
import com.clienthub.application.dto.project.ProjectMemberRequest;
import com.clienthub.application.dto.project.ProjectMemberResponse;
import com.clienthub.application.dto.project.ProjectFreelancerSearchResponse;
import com.clienthub.application.dto.project.ProjectRequest;
import com.clienthub.application.dto.project.ProjectResponse;
import com.clienthub.application.dto.audit.UserAuditProofResponse;
import com.clienthub.infrastructure.security.CustomUserDetails;
import com.clienthub.application.service.ProjectPortalService;
import com.clienthub.application.service.ProjectService;
import com.clienthub.application.service.AnalyticsService;
import com.clienthub.application.dto.analytics.ProjectProgressResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "Project management and client portal endpoints")
public class ProjectController {

    private final ProjectService projectService;
    private final AnalyticsService analyticsService;
    private final ProjectPortalService projectPortalService;

    public ProjectController(ProjectService projectService,
                             AnalyticsService analyticsService,
                             ProjectPortalService projectPortalService) {
        this.projectService = projectService;
        this.analyticsService = analyticsService;
        this.projectPortalService = projectPortalService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody ProjectRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        ProjectResponse response = projectService.createProject(request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    public ResponseEntity<Page<ProjectResponse>> getProjects(
            @RequestParam(required = false) ProjectStatus status,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Role callerRole = Role.valueOf(currentUser.getRole());
        Page<ProjectResponse> response = projectService.getProjects(status, pageable, currentUser.getId(), callerRole);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    public ResponseEntity<ProjectResponse> getProjectById(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        Role callerRole = Role.valueOf(currentUser.getRole());
        return ResponseEntity.ok(projectService.getProjectById(id, currentUser.getId(), callerRole));
    }

    @GetMapping("/{id}/progress")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    @Operation(summary = "Get project progress", description = "Returns backend-computed task completion progress for a project.")
    public ResponseEntity<ProjectProgressResponse> getProjectProgress(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        Role callerRole = Role.valueOf(currentUser.getRole());
        return ResponseEntity.ok(
                analyticsService.getProjectProgress(id, currentUser.getId(), callerRole));
    }

    @GetMapping("/{id}/files")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    @Operation(summary = "Get project portal files", description = "Aggregates attachments from project, task, and invoice comments for the project portal.")
    public ResponseEntity<List<ProjectFileResponse>> getProjectFiles(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        Role callerRole = Role.valueOf(currentUser.getRole());
        return ResponseEntity.ok(projectPortalService.getProjectFiles(id, currentUser.getId(), callerRole));
    }

    @GetMapping("/{id}/activity")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    @Operation(summary = "Get project portal activity", description = "Returns a client-safe timeline for project, task, invoice, and message activity.")
    public ResponseEntity<Page<ProjectActivityResponse>> getProjectActivity(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Role callerRole = Role.valueOf(currentUser.getRole());
        return ResponseEntity.ok(projectPortalService.getProjectActivity(id, currentUser.getId(), callerRole, pageable));
    }

    @GetMapping("/{id}/activity/{auditLogId}/proof")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    @Operation(summary = "Get a project activity audit proof")
    public ResponseEntity<UserAuditProofResponse> getProjectActivityProof(
            @PathVariable UUID id,
            @PathVariable long auditLogId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        Role callerRole = Role.valueOf(currentUser.getRole());
        return ResponseEntity.ok(projectPortalService.getActivityProof(
                id, auditLogId, currentUser.getId(), callerRole, false));
    }

    @PostMapping("/{id}/activity/{auditLogId}/verify")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    @Operation(summary = "Verify a project activity audit proof")
    public ResponseEntity<UserAuditProofResponse> verifyProjectActivityProof(
            @PathVariable UUID id,
            @PathVariable long auditLogId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        Role callerRole = Role.valueOf(currentUser.getRole());
        return ResponseEntity.ok(projectPortalService.getActivityProof(
                id, auditLogId, currentUser.getId(), callerRole, true));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        boolean isAdmin = "ADMIN".equals(currentUser.getRole());
        return ResponseEntity.ok(projectService.updateProject(id, request, currentUser.getId(), isAdmin));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<Void> deleteProject(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        boolean isAdmin = "ADMIN".equals(currentUser.getRole());
        projectService.deleteProject(id, currentUser.getId(), isAdmin);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<ProjectMemberResponse> addProjectMember(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectMemberRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        boolean isAdmin = "ADMIN".equals(currentUser.getRole());
        ProjectMemberResponse response = projectService.addMember(id, request.getUserId(), currentUser.getId(), isAdmin);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<Void> removeProjectMember(
            @PathVariable UUID id,
            @PathVariable UUID userId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        boolean isAdmin = "ADMIN".equals(currentUser.getRole());
        projectService.removeMember(id, userId, currentUser.getId(), isAdmin);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    public ResponseEntity<List<ProjectMemberResponse>> getProjectMembers(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        Role callerRole = Role.valueOf(currentUser.getRole());
        return ResponseEntity.ok(projectService.getProjectMembers(id, currentUser.getId(), callerRole));
    }

    @GetMapping("/{id}/freelancers/search")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<List<ProjectFreelancerSearchResponse>> searchProjectFreelancers(
            @PathVariable UUID id,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        boolean isAdmin = "ADMIN".equals(currentUser.getRole());
        return ResponseEntity.ok(projectService.searchAvailableFreelancers(id, keyword, currentUser.getId(), isAdmin));
    }
}
