package com.clienthub.core.repository;

import com.clienthub.core.domain.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    List<AuditLog> findByEntityTypeAndEntityIdAndTenantIdOrderByCreatedAtDesc(
            String entityType,
            String entityId,
            String tenantId
    );

    List<AuditLog> findByIsAnchoredFalse(Pageable pageable);
}
