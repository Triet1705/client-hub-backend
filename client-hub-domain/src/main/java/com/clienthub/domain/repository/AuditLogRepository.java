package com.clienthub.domain.repository;

import com.clienthub.domain.entity.AuditLog;
import com.clienthub.domain.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    Page<AuditLog> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    List<AuditLog> findByEntityTypeAndEntityIdAndTenantIdOrderByCreatedAtDesc(
            String entityType,
            String entityId,
            String tenantId
    );

    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.tenantId = :tenantId
              AND (
                (a.entityType = 'PROJECT' AND a.entityId = :projectId)
                OR (a.entityType = 'TASK' AND a.entityId IN :taskIds)
                OR (a.entityType = 'INVOICE' AND a.entityId IN :invoiceIds)
                OR (a.entityType = 'COMMENT' AND a.entityId IN :commentIds)
              )
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> findProjectActivity(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("taskIds") Collection<String> taskIds,
            @Param("invoiceIds") Collection<String> invoiceIds,
            @Param("commentIds") Collection<String> commentIds,
            Pageable pageable
    );

    List<AuditLog> findByIsAnchoredFalse(Pageable pageable);

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByActionAndCreatedAtAfter(AuditAction action, Instant createdAt);

    long countByIsAnchoredFalse();
}
