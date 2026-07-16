package com.clienthub.domain.repository;

import com.clienthub.domain.entity.AuditAnchorMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface AuditAnchorMemberRepository extends JpaRepository<AuditAnchorMember, Long> {
    @EntityGraph(attributePaths = "batch")
    Optional<AuditAnchorMember> findByAuditLogId(Long auditLogId);
    @EntityGraph(attributePaths = "batch")
    List<AuditAnchorMember> findByAuditLogIdIn(Collection<Long> auditLogIds);
    boolean existsByAuditLogId(Long auditLogId);
}
