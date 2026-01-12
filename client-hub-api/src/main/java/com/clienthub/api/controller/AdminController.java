package com.clienthub.api.controller;

import com.clienthub.core.dto.UserSearchRequest;
import com.clienthub.core.dto.UserSummaryDto;
import com.clienthub.core.service.UserService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Object> getAdminDashboard() {
        return ResponseEntity.ok(Map.of(
                "message", "Welcome to Admin Dashboard",
                "status", "Authorize",
                "timestamp", System.currentTimeMillis()
        ));
    }

    @GetMapping("/system-status")
    public ResponseEntity<Object> getSystemStatus() {
        return ResponseEntity.ok(Map.of(
                "system", "Client Hub Core",
                "health", "Stable",
                "active_connections", 1
        ));
    }

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
}
