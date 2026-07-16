package com.clienthub.web.controller;

import com.clienthub.application.dto.admin.*;
import com.clienthub.application.dto.analytics.AdminDashboardResponse;
import com.clienthub.application.service.AdminService;
import com.clienthub.domain.enums.AuditAction;
import com.clienthub.domain.enums.AuditAnchorBatchStatus;
import com.clienthub.domain.enums.AuditRecordAnchorStatus;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.clienthub.infrastructure.security.CustomUserDetails;
import com.clienthub.web3.service.AuditAnchorBatchResponse;
import com.clienthub.web3.service.AuditAnchorService;
import com.clienthub.web3.service.AuditProofResponse;
import com.clienthub.web3.service.AuditAnchorSummaryResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration", description = "Platform Super-Admin Endpoints")
public class AdminController {

    private final AdminService adminService;
    private final AuditAnchorService auditAnchorService;

    public AdminController(AdminService adminService, AuditAnchorService auditAnchorService) {
        this.adminService = adminService;
        this.auditAnchorService = auditAnchorService;
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

    @GetMapping("/control-center")
    @Operation(summary = "Get admin control center overview",
               description = "Admin-only operational aggregate for platform metrics, health, alerts, events, audit feed, and read-only flags")
    public ResponseEntity<AdminControlCenterResponse> getControlCenter() {
        return ResponseEntity.ok(adminService.getControlCenter());
    }

    @GetMapping("/health")
    @Operation(summary = "Get system health",
               description = "Checks DB, Redis, AI Engine, blockchain readiness, JVM memory, and uptime")
    public ResponseEntity<AdminHealthResponse> getSystemHealth() {
        return ResponseEntity.ok(adminService.getSystemHealth());
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Get recent system activity",
               description = "Paginated security and compliance audit logs across all tenants")
    public ResponseEntity<Page<AdminAuditLogResponse>> listRecentActivity(
            @Parameter(description = "Audit action filter") @RequestParam(required = false) AuditAction action,
            @Parameter(description = "Entity type filter") @RequestParam(required = false) String entityType,
            @Parameter(description = "Tenant ID filter") @RequestParam(required = false) String tenantId,
            @Parameter(description = "Anchored status filter") @RequestParam(required = false) Boolean anchored,
            @Parameter(description = "Anchor workflow state: WAITING, PENDING, VERIFIED, FAILED")
            @RequestParam(required = false) AuditRecordAnchorStatus anchorStatus,
            @Parameter(description = "Created at lower bound") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Created at upper bound") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(adminService.listRecentActivity(
                action,
                entityType,
                tenantId,
                anchored,
                anchorStatus,
                from,
                to,
                buildPageable(page, size, "createdAt", "desc")));
    }

    @GetMapping("/audit-anchor-batches")
    @Operation(summary = "List audit anchor batches",
               description = "Returns blockchain audit-proof batches and their submission or confirmation state")
    public ResponseEntity<Page<AuditAnchorBatchResponse>> listAuditAnchorBatches(
            @RequestParam(required = false) AuditAnchorBatchStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(auditAnchorService.listBatches(status, PageRequest.of(page, size)));
    }

    @GetMapping("/audit-anchor-summary")
    @Operation(summary = "Get audit anchoring operational summary")
    public ResponseEntity<AuditAnchorSummaryResponse> getAuditAnchorSummary() {
        return ResponseEntity.ok(auditAnchorService.summary());
    }

    @PostMapping("/audit-anchor-batches/run")
    @Operation(summary = "Run audit anchoring now",
               description = "Creates and submits a batch from currently unanchored audit records")
    public ResponseEntity<AuditAnchorBatchResponse> runAuditAnchoring() {
        return auditAnchorService.run(true)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/audit-logs/{id}/proof")
    @Operation(summary = "Get an audit log Merkle proof")
    public ResponseEntity<AuditProofResponse> getAuditProof(@PathVariable long id) {
        return ResponseEntity.ok(auditAnchorService.getProof(id));
    }

    @PostMapping("/audit-logs/{id}/verify")
    @Operation(summary = "Verify an audit log proof against the configured blockchain")
    public ResponseEntity<AuditProofResponse> verifyAuditProof(@PathVariable long id) {
        return ResponseEntity.ok(auditAnchorService.verify(id));
    }

    @GetMapping("/events")
    @Operation(summary = "Get normalized admin events",
               description = "Admin-only domain event timeline derived from audit logs for operational visibility")
    public ResponseEntity<Page<AdminEventItem>> listEvents(
            @Parameter(description = "Category: AUTH, USER, PROJECT, TASK, INVOICE, AUDIT, SYSTEM, WEB3") @RequestParam(required = false) String category,
            @Parameter(description = "Severity: INFO, SUCCESS, WARNING, CRITICAL") @RequestParam(required = false) String severity,
            @Parameter(description = "Entity type filter") @RequestParam(required = false) String entityType,
            @Parameter(description = "Tenant ID filter") @RequestParam(required = false) String tenantId,
            @Parameter(description = "Created at lower bound") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Created at upper bound") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(adminService.listEvents(
                category,
                severity,
                entityType,
                tenantId,
                from,
                to,
                buildPageable(page, size, "createdAt", "desc")));
    }

    @GetMapping("/flags")
    @Operation(summary = "Get read-only platform capability flags",
               description = "Admin-only runtime/configuration visibility for operational capabilities; flags cannot be mutated here")
    public ResponseEntity<List<AdminFeatureFlag>> listFlags() {
        return ResponseEntity.ok(adminService.getFeatureFlags());
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
