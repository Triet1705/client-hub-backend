package com.clienthub.api.controller;

import com.clienthub.core.domain.enums.ProjectStatus;
import com.clienthub.core.dto.project.ProjectRequest;
import com.clienthub.core.dto.project.ProjectResponse;
import com.clienthub.core.security.CustomUserDetails;
import com.clienthub.core.service.ProjectService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CLIENT')")
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
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<ProjectResponse> response = projectService.getProjects(status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    public ResponseEntity<ProjectResponse> getProjectById(@PathVariable UUID id) {
        return ResponseEntity.ok(projectService.getProjectById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CLIENT')") 
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(projectService.updateProject(id, request, currentUser.getId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<Void> deleteProject(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        projectService.deleteProject(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}