package com.clienthub.web.controller;

import com.clienthub.web.dto.auth.JwtResponse;
import com.clienthub.domain.entity.RefreshToken;
import com.clienthub.domain.entity.User;
import com.clienthub.application.dto.UserSearchRequest;
import com.clienthub.application.dto.UserSummaryDto;
import com.clienthub.application.exception.ResourceNotFoundException;
import com.clienthub.infrastructure.security.CustomUserDetails;
import com.clienthub.infrastructure.security.JwtTokenProvider;
import com.clienthub.application.service.AuthService;
import com.clienthub.application.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Controller for Administrative operations.
 * Includes System Monitoring, User Management, and Impersonation (Support).
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final UserService userService;
    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.expiration:900000}")
    private long jwtExpirationMs;

    public AdminController(UserService userService,
                           AuthService authService,
                           JwtTokenProvider jwtTokenProvider) {
        this.userService = userService;
        this.authService = authService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * GET /api/admin/dashboard
     * Basic admin dashboard stats/welcome.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Object> getAdminDashboard() {
        return ResponseEntity.ok(Map.of(
                "message", "Welcome to Admin Dashboard",
                "status", "Authorized",
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * GET /api/admin/system-status
     * System health check for admins.
     */
    @GetMapping("/system-status")
    public ResponseEntity<Object> getSystemStatus() {
        return ResponseEntity.ok(Map.of(
                "system", "Client Hub Core",
                "health", "Stable",
                "active_connections", 1 // Placeholder for real metrics
        ));
    }

    /**
     * GET /api/admin/users
     * List all users system-wide with filtering.
     */
    @GetMapping("/users")
    public ResponseEntity<Page<UserSummaryDto>> getAllUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        UserSearchRequest request = new UserSearchRequest(
                keyword, role, active, page, pageSize, sortBy, sortDir
        );

        Page<UserSummaryDto> users = userService.findAllUsersSystemWide(request);
        return ResponseEntity.ok(users);
    }

    /**
     * POST /api/admin/users/{userId}/impersonate
     * Generate an access token for a specific user to debug issues.
     * * Ref: TDD Section 15 - Admin Portal + Impersonation
     * Security: Only ADMIN role can access.
     * Audit: Logs the impersonation action.
     */
    @PostMapping("/users/{userId}/impersonate")
    public ResponseEntity<JwtResponse> impersonateUser(
            @PathVariable UUID userId,
            @AuthenticationPrincipal CustomUserDetails admin,
            HttpServletRequest request) {

        logger.info("Admin {} requesting impersonation for User {}", admin.getId(), userId);

        User targetUser = userService.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (targetUser.getRole().name().equals("ADMIN")) {
            logger.warn("Security Alert: Admin {} attempted to impersonate another Admin {}", admin.getId(), userId);
        }

        String accessToken = jwtTokenProvider.generateImpersonationToken(
                targetUser.getId(),
                targetUser.getEmail(),
                targetUser.getRole().name(),
                targetUser.getTenantId(),
                admin.getId()
        );

        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        RefreshToken refreshTokenEntity = authService.createRefreshTokenForUser(targetUser, ipAddress, userAgent);

        long expiresIn = jwtExpirationMs / 1000;

        // TODO: Replace with AuditService.logAction() when Module Audit is implemented
        logger.warn("[AUDIT] IMPERSONATION_STARTED | Admin: {} | Target: {} | Tenant: {}",
                admin.getId(), targetUser.getId(), targetUser.getTenantId());

        return ResponseEntity.ok(new JwtResponse(
                accessToken,
                refreshTokenEntity.getToken(),
                expiresIn,
                targetUser.getId(),
                targetUser.getEmail(),
                targetUser.getRole().name(),
                targetUser.getTenantId()
        ));
    }
}