package com.clienthub.domain.repository;

import com.clienthub.domain.entity.AuditAnchorBatch;
import com.clienthub.domain.enums.AuditAnchorBatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface AuditAnchorBatchRepository extends JpaRepository<AuditAnchorBatch, UUID> {
    Page<AuditAnchorBatch> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<AuditAnchorBatch> findByStatusOrderByCreatedAtDesc(AuditAnchorBatchStatus status, Pageable pageable);
    List<AuditAnchorBatch> findByStatusInOrderByCreatedAtAsc(Collection<AuditAnchorBatchStatus> statuses);
    long countByStatus(AuditAnchorBatchStatus status);
    long countByStatusIn(Collection<AuditAnchorBatchStatus> statuses);
    Optional<AuditAnchorBatch> findFirstByStatusOrderByConfirmedAtDesc(AuditAnchorBatchStatus status);
}
