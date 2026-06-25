package com.clienthub.domain.repository;

import com.clienthub.domain.entity.AuditLog;
import com.clienthub.domain.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    Page<AuditLog> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    List<AuditLog> findByEntityTypeAndEntityIdAndTenantIdOrderByCreatedAtDesc(
            String entityType,
            String entityId,
            String tenantId
    );

    List<AuditLog> findByIsAnchoredFalse(Pageable pageable);

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByActionAndCreatedAtAfter(AuditAction action, Instant createdAt);

    long countByIsAnchoredFalse();
}
