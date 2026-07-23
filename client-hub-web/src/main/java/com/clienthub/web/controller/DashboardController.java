package com.clienthub.web.controller;

import com.clienthub.application.dto.dashboard.DashboardStatsResponse;
import com.clienthub.application.service.DashboardService;
import com.clienthub.infrastructure.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    public ResponseEntity<DashboardStatsResponse> getDashboardSummary(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(dashboardService.getSummaryStats(currentUser.getId()));
    }
}
