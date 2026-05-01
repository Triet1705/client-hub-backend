package com.clienthub.web.controller;

import com.clienthub.application.dto.analytics.ResponseRateResponse;
import com.clienthub.application.dto.analytics.TrustScoreResponse;
import com.clienthub.application.service.AnalyticsService;
import com.clienthub.common.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics", description = "Endpoints for platform analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/trust-score")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENT', 'FREELANCER')")
    @Operation(summary = "Get Trust Score", description = "Calculates trust score based on paid invoices")
    public ResponseEntity<TrustScoreResponse> getTrustScore() {
        return ResponseEntity.ok(analyticsService.getTrustScore(TenantContext.getTenantId()));
    }

    @GetMapping("/active-response-rate")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENT', 'FREELANCER')")
    @Operation(summary = "Get Active Response Rate", description = "Calculates response rate based on thread participation")
    public ResponseEntity<ResponseRateResponse> getResponseRate() {
        return ResponseEntity.ok(analyticsService.getResponseRate(TenantContext.getTenantId()));
    }
}
