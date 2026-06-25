package com.clienthub.application.dto.admin;

import java.time.Instant;

public record AdminEventItem(
        Long id,
        String category,
        String severity,
        String title,
        String description,
        String actorEmail,
        String tenantId,
        String entityType,
        String entityId,
        Instant occurredAt
) {}
