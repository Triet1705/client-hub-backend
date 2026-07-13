package com.clienthub.application.dto.admin;

import com.clienthub.domain.entity.AuditLog;

import java.time.Instant;
import java.util.UUID;

public record AdminAuditLogResponse(
        Long id,
        String action,
        String entityType,
        String entityId,
        String userEmail,
        String userRole,
        UUID userId,
        String tenantId,
        String ipAddress,
        Instant createdAt,
        String oldValue,
        String newValue,
        String dataHash,
        boolean isAnchored
) {
    public static AdminAuditLogResponse from(AuditLog log) {
        return from(log, log.isAnchored());
    }

    public static AdminAuditLogResponse from(AuditLog log, boolean anchored) {
        return new AdminAuditLogResponse(
                log.getId(),
                log.getAction() != null ? log.getAction().name() : "UNKNOWN",
                log.getEntityType(),
                log.getEntityId(),
                log.getUserEmail(),
                log.getUserRole(),
                log.getUserId(),
                log.getTenantId(),
                log.getIpAddress(),
                log.getCreatedAt(),
                log.getOldValue(),
                log.getNewValue(),
                log.getDataHash(),
                anchored
        );
    }
}
