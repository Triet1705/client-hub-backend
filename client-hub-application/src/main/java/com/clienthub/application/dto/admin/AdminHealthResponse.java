package com.clienthub.application.dto.admin;

import java.time.Instant;

public record AdminHealthResponse(
        String overallStatus,
        ComponentHealth database,
        ComponentHealth redis,
        ComponentHealth aiEngine,
        ComponentHealth blockchain,
        JvmVitals jvm,
        long uptimeSeconds,
        Instant checkedAt
) {

    public AdminHealthResponse(
            String overallStatus,
            ComponentHealth database,
            ComponentHealth redis,
            ComponentHealth aiEngine) {
        this(
                overallStatus,
                database,
                redis,
                aiEngine,
                new ComponentHealth("UP", "Not configured", 0),
                JvmVitals.current(),
                0,
                Instant.now());
    }
}
