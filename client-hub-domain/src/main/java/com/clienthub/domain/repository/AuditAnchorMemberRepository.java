package com.clienthub.domain.repository;

import com.clienthub.domain.entity.AuditAnchorMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;

public interface AuditAnchorMemberRepository extends JpaRepository<AuditAnchorMember, Long> {
    @EntityGraph(attributePaths = "batch")
    Optional<AuditAnchorMember> findByAuditLogId(Long auditLogId);
    boolean existsByAuditLogId(Long auditLogId);
}
