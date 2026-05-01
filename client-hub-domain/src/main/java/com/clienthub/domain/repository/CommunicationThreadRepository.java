package com.clienthub.domain.repository;

import com.clienthub.domain.enums.CommentTargetType;
import com.clienthub.domain.entity.CommunicationThread;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommunicationThreadRepository extends JpaRepository<CommunicationThread, Long> {
    Optional<CommunicationThread> findByTargetTypeAndTargetIdAndTenantId(
            CommentTargetType targetType,
            String targetId,
            String tenantId
    );

    long countByTenantId(String tenantId);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(t) FROM CommunicationThread t WHERE t.tenantId = :tenantId AND (SELECT COUNT(DISTINCT c.author.id) FROM Comment c WHERE c.thread = t) >= 2")
    long countThreadsWithMultipleParticipantsByTenantId(@org.springframework.data.repository.query.Param("tenantId") String tenantId);
}
