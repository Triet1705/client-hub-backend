package com.clienthub.domain.repository;

import com.clienthub.domain.entity.AuditLog;
import com.clienthub.domain.enums.AuditAction;
import com.clienthub.domain.enums.AuditAnchorBatchStatus;
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
import java.util.Optional;

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

    @Query(value = """
            SELECT a.* FROM audit_logs a
            WHERE a.action NOT IN ('ANCHOR_SUCCESS', 'ANCHOR_FAILED')
              AND NOT EXISTS (
                SELECT 1 FROM audit_anchor_members m WHERE m.audit_log_id = a.id
              )
            ORDER BY a.id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<AuditLog> findUnassignedForAnchoring(@Param("limit") int limit);

    @Query("""
            SELECT MIN(a.createdAt) FROM AuditLog a
            WHERE a.action NOT IN :excludedActions
              AND NOT EXISTS (
                SELECT m.id FROM AuditAnchorMember m WHERE m.auditLogId = a.id
              )
            """)
    Optional<Instant> findOldestUnassignedCreatedAt(
            @Param("excludedActions") Collection<AuditAction> excludedActions);

    @Query("""
            SELECT COUNT(a) FROM AuditLog a
            WHERE a.action NOT IN :excludedActions
              AND NOT EXISTS (
                SELECT m.id FROM AuditAnchorMember m WHERE m.auditLogId = a.id
              )
            """)
    long countUnassignedForAnchoring(@Param("excludedActions") Collection<AuditAction> excludedActions);

    @Query("""
            SELECT COUNT(a) FROM AuditLog a
            WHERE a.action NOT IN :excludedActions
              AND NOT EXISTS (
                SELECT m.id FROM AuditAnchorMember m
                WHERE m.auditLogId = a.id AND m.batch.status = :confirmedStatus
              )
            """)
    long countWithoutConfirmedAnchor(
            @Param("excludedActions") Collection<AuditAction> excludedActions,
            @Param("confirmedStatus") AuditAnchorBatchStatus confirmedStatus);

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByActionAndCreatedAtAfter(AuditAction action, Instant createdAt);

    long countByIsAnchoredFalse();
}
