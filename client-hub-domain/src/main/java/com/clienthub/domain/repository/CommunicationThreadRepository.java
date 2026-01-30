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
}
