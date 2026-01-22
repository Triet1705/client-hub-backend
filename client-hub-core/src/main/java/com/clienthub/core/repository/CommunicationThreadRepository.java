package com.clienthub.core.repository;

import com.clienthub.core.domain.enums.CommentTargetType;
import com.clienthub.core.domain.entity.CommunicationThread;
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
}
