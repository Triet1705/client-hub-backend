package com.clienthub.application.dto.admin;

public record AdminHealthResponse(
        String overallStatus,
        ComponentHealth database,
        ComponentHealth redis,
        ComponentHealth aiEngine
) {}
