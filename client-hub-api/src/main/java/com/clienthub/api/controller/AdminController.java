package com.clienthub.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
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
}
