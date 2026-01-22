package com.clienthub.core.repository;

import com.clienthub.core.domain.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientIdAndTenantIdOrderByCreatedAtDesc(
            UUID recipientId,
            String tenantId,
            Pageable pageable
    );

    long countByRecipientIdAndTenantIdAndIsReadFalse(UUID recipientId, String tenantId);
}