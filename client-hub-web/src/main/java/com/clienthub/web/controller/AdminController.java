package com.clienthub.web.controller;

import com.clienthub.application.dto.admin.*;
import com.clienthub.application.dto.analytics.AdminDashboardResponse;
import com.clienthub.application.service.AdminService;
import com.clienthub.domain.enums.Role;
import com.clienthub.web.dto.admin.ForceStatusRequest;
import com.clienthub.web.dto.admin.UserRoleRequest;
import com.clienthub.web.dto.admin.UserStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.clienthub.infrastructure.security.CustomUserDetails;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration", description = "Platform Super-Admin Endpoints")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // ─── Helper: build a safe Pageable from explicit params ───────────────────
    private Pageable buildPageable(int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        return PageRequest.of(page, size, sort);
    }

    // ─── Users ────────────────────────────────────────────────────────────────

    @GetMapping("/users")
    @Operation(summary = "List all users platform-wide",
               description = "Paginated list of all users, filterable by role and active status")
    public ResponseEntity<Page<AdminUserResponse>> listUsers(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String keyword,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction: asc or desc") @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(adminService.listUsers(role, active, keyword, buildPageable(page, size, sortBy, sortDir)));
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get single user detail", description = "Includes project count and invoice count")
    public ResponseEntity<AdminUserDetailResponse> getUserDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.getUserDetail(id));
    }

    @PatchMapping("/users/{id}/status")
    @Operation(summary = "Activate or Deactivate user")
    public ResponseEntity<Void> updateUserStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UserStatusRequest request) {
        adminService.updateUserStatus(id, request.active());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/users/{id}/role")
    @Operation(summary = "Change user role")
    public ResponseEntity<Void> updateUserRole(
            @PathVariable UUID id,
            @Valid @RequestBody UserRoleRequest request) {
        adminService.updateUserRole(id, request.role());
        return ResponseEntity.noContent().build();
    }

    // ─── Analytics & Health ───────────────────────────────────────────────────

    @GetMapping("/analytics")
    @Operation(summary = "Get platform-wide analytics",
               description = "Total users, projects, invoices, and revenue across all tenants")
    public ResponseEntity<AdminDashboardResponse> getPlatformAnalytics() {
        return ResponseEntity.ok(adminService.getPlatformAnalytics());
    }

    @GetMapping("/health")
    @Operation(summary = "Get system health",
               description = "Checks DB, Redis, and AI Engine connectivity")
    public ResponseEntity<AdminHealthResponse> getSystemHealth() {
        return ResponseEntity.ok(adminService.getSystemHealth());
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Get recent system activity",
               description = "Paginated list of audit logs across all tenants")
    public ResponseEntity<Page<AdminAuditLogResponse>> listRecentActivity(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(adminService.listRecentActivity(buildPageable(page, size, "createdAt", "desc")));
    }

    // ─── Projects ─────────────────────────────────────────────────────────────

    @GetMapping("/projects")
    @Operation(summary = "List all projects platform-wide",
               description = "Paginated list of all projects across all tenants")
    public ResponseEntity<Page<AdminProjectResponse>> listAllProjects(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction: asc or desc") @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(adminService.listAllProjects(buildPageable(page, size, sortBy, sortDir)));
    }

    // ─── Invoices ─────────────────────────────────────────────────────────────

    @GetMapping("/invoices")
    @Operation(summary = "List all invoices platform-wide",
               description = "Paginated list of all invoices across all tenants")
    public ResponseEntity<Page<AdminInvoiceResponse>> listAllInvoices(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction: asc or desc") @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(adminService.listAllInvoices(buildPageable(page, size, sortBy, sortDir)));
    }

    @PatchMapping("/invoices/{id}/force-status")
    @Operation(summary = "Force invoice to any status",
               description = "Bypasses normal transition rules. Requires a reason.")
    public ResponseEntity<Void> forceInvoiceStatus(
            @PathVariable Long id,
            @Valid @RequestBody ForceStatusRequest request,
            @AuthenticationPrincipal CustomUserDetails admin) {
        adminService.forceInvoiceStatus(id, request.status(), request.reason(), admin.getId());
        return ResponseEntity.noContent().build();
    }

    // ─── Impersonation ────────────────────────────────────────────────────────

    @PostMapping("/impersonate/{userId}")
    @Operation(summary = "Generate impersonation token for target user",
               description = "SECURITY NOTICE: Logs impersonation event. Returns JWT containing impersonator_id claim.")
    public ResponseEntity<ImpersonationResponse> impersonate(
            @PathVariable UUID userId,
            @AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok(adminService.impersonate(userId, admin.getId()));
    }
}