package com.clienthub.application.dto.admin;

public record AdminFeatureFlag(
        String key,
        String label,
        boolean enabled,
        String status,
        String description,
        String source
) {}
