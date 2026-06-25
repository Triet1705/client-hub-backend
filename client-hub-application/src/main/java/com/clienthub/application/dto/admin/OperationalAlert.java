package com.clienthub.application.dto.admin;

import java.time.Instant;

public record OperationalAlert(
        String id,
        String severity,
        String title,
        String message,
        String recommendedAction,
        Instant createdAt
) {}
