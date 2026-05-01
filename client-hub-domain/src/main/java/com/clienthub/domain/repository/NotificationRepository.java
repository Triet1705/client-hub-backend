package com.clienthub.domain.repository;

import com.clienthub.domain.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientIdAndTenantIdOrderByCreatedAtDesc(
            UUID recipientId,
            String tenantId,
            Pageable pageable
    );

        Page<Notification> findByRecipientIdAndTenantIdAndIsReadFalseOrderByCreatedAtDesc(
            UUID recipientId,
            String tenantId,
            Pageable pageable
        );

    Optional<Notification> findByIdAndRecipientIdAndTenantId(Long id, UUID recipientId, String tenantId);

    long countByRecipientIdAndTenantIdAndIsReadFalse(UUID recipientId, String tenantId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipient.id = :recipientId AND n.tenantId = :tenantId AND n.isRead = false")
    int markAllAsReadByRecipientAndTenant(@Param("recipientId") UUID recipientId, @Param("tenantId") String tenantId);
}