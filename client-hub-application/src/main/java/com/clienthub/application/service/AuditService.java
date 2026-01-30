// ### File: client-hub-core/src/main/java/com/clienthub/core/service/AuditService.java
package com.clienthub.application.service;

import com.clienthub.common.context.TenantContext;
import com.clienthub.domain.entity.AuditLog;
import com.clienthub.domain.enums.AuditAction;
import com.clienthub.domain.repository.AuditLogRepository;
import com.clienthub.infrastructure.security.CustomUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditAction action, String entityType, String entityId,
                    Object oldEntity, Object newEntity, String ipAddress) {
        try {
            String tenantId = TenantContext.getTenantId();
            if (tenantId == null) {
                tenantId = "SYSTEM";
            }

            UUID userId = null;
            String userEmail = "SYSTEM";

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
                userId = userDetails.getId();
                userEmail = userDetails.getEmail();
            }

            String oldValJson = oldEntity != null ? serialize(oldEntity) : null;
            String newValJson = newEntity != null ? serialize(newEntity) : null;

            String integrityHash = computeHash(tenantId, entityId, action, newValJson);

            AuditLog log = new AuditLog(
                    tenantId,
                    userId,
                    userEmail,
                    action,
                    entityType,
                    entityId,
                    oldValJson,
                    newValJson,
                    ipAddress,
                    integrityHash
            );

            auditLogRepository.save(log);

            logger.debug("Audit log saved: {} on {}/{}", action, entityType, entityId);

        } catch (Exception e) {
            logger.error("CRITICAL: Failed to save audit log for {} {}", entityType, entityId, e);
        }
    }

    private String serialize(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            logger.warn("JSON Serialization failed for audit", e);
            return "{\"error\": \"Serialization Failed\"}";
        }
    }

    private String computeHash(String tenantId, String entityId, AuditAction action, String data) {
        try {
            String raw = tenantId + "|" + entityId + "|" + action + "|" + (data == null ? "" : data);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            logger.error("Hashing failed", e);
            return "HASH_ERROR";
        }
    }
}