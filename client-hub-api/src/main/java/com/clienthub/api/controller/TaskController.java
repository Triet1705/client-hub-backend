package com.clienthub.api.controller;

import com.clienthub.core.domain.enums.TaskStatus;
import com.clienthub.core.dto.task.TaskRequest;
import com.clienthub.core.dto.task.TaskResponse;
import com.clienthub.core.security.CustomUserDetails;
import com.clienthub.core.service.TaskService;
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

/**
 * REST Controller for Task management endpoints.
 * Implements CRUD operations with multi-tenancy security and validation.
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * Create a new task.
     * 
     * POST /api/tasks
     * 
     * @param request the task creation request
     * @return the created task response with 201 status
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody TaskRequest request) {
        TaskResponse response = taskService.createTask(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get all tasks with optional filters.
     * 
     * GET /api/tasks?projectId={uuid}&status={status}&assignedToId={uuid}
     * 
     * @param projectId optional project filter
     * @param status optional status filter
     * @param assignedToId optional assignee filter
     * @param pageable pagination parameters
     * @return page of task responses
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    public ResponseEntity<Page<TaskResponse>> getTasks(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) UUID assignedToId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<TaskResponse> response = taskService.getTasks(projectId, status, assignedToId, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Get a single task by ID.
     * 
     * GET /api/tasks/{id}
     * 
     * @param id the task ID
     * @return the task response
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    public ResponseEntity<TaskResponse> getTaskById(@PathVariable UUID id) {
        TaskResponse response = taskService.getTaskById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Update an existing task.
     * 
     * PUT /api/tasks/{id}
     * 
     * @param id the task ID
     * @param request the update request
     * @return the updated task response
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    public ResponseEntity<TaskResponse> updateTask(
            @PathVariable UUID id,
            @Valid @RequestBody TaskRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        TaskResponse response = taskService.updateTask(id, request, userDetails.getId());
        return ResponseEntity.ok(response);
    }

    /**
     * Update only the status of a task.
     * 
     * PATCH /api/tasks/{id}/status
     * 
     * @param id the task ID
     * @param status the new status
     * @return the updated task response
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    public ResponseEntity<TaskResponse> updateTaskStatus(
            @PathVariable UUID id,
            @RequestParam TaskStatus status) {

        TaskResponse response = taskService.updateTaskStatus(id, status);
        return ResponseEntity.ok(response);
    }

    /**
     * Assign a task to a user.
     * 
     * PATCH /api/tasks/{id}/assign
     * 
     * @param id the task ID
     * @param userId the user ID to assign to
     * @return the updated task response
     */
    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    public ResponseEntity<TaskResponse> assignTask(
            @PathVariable UUID id,
            @RequestParam UUID userId) {

        TaskResponse response = taskService.assignTask(id, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Unassign a task (remove assignee).
     * 
     * PATCH /api/tasks/{id}/unassign
     * 
     * @param id the task ID
     * @return the updated task response
     */
    @PatchMapping("/{id}/unassign")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    public ResponseEntity<TaskResponse> unassignTask(@PathVariable UUID id) {
        TaskResponse response = taskService.unassignTask(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Soft delete a task.
     * 
     * DELETE /api/tasks/{id}
     * 
     * @param id the task ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<Void> deleteTask(@PathVariable UUID id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }
}
