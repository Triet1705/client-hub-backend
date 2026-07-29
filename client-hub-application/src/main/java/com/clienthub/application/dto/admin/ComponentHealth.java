package com.clienthub.application.dto.admin;

public record ComponentHealth(
        String status,
        String label,
        Long latencyMs,
        boolean enabled,
        boolean required
) {}
