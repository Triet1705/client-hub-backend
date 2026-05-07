package com.clienthub.application.service;

import com.clienthub.application.dto.notification.MarkAllReadResponse;
import com.clienthub.application.dto.notification.NotificationResponse;
import com.clienthub.application.dto.notification.UnreadCountResponse;
import com.clienthub.application.exception.ResourceNotFoundException;
import com.clienthub.common.service.TenantAwareService;
import com.clienthub.domain.entity.Notification;
import com.clienthub.domain.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class NotificationService extends TenantAwareService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(UUID recipientId, Pageable pageable, boolean unreadOnly) {
        String tenantId = getCurrentTenantId();

        Page<Notification> notifications;
        if (unreadOnly) {
            notifications = notificationRepository
                    .findByRecipientIdAndTenantIdAndIsReadFalseOrderByCreatedAtDesc(recipientId, tenantId, pageable);
        } else {
            notifications = notificationRepository
                    .findByRecipientIdAndTenantIdOrderByCreatedAtDesc(recipientId, tenantId, pageable);
        }

        return notifications.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(UUID recipientId) {
        String tenantId = getCurrentTenantId();
        long unreadCount = notificationRepository.countByRecipientIdAndTenantIdAndIsReadFalse(recipientId, tenantId);
        return new UnreadCountResponse(unreadCount);
    }

    public NotificationResponse markAsRead(Long notificationId, UUID recipientId) {
        String tenantId = getCurrentTenantId();

        Notification notification = notificationRepository
                .findByIdAndRecipientIdAndTenantId(notificationId, recipientId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(java.time.Instant.now());
            notification = notificationRepository.save(notification);
        }

        return toResponse(notification);
    }

    public MarkAllReadResponse markAllAsRead(UUID recipientId) {
        String tenantId = getCurrentTenantId();
        int updatedCount = notificationRepository.markAllAsReadByRecipientAndTenant(recipientId, tenantId, Instant.now());
        return new MarkAllReadResponse(updatedCount);
    }

    private NotificationResponse toResponse(Notification notification) {
        NotificationResponse response = new NotificationResponse();
        response.setId(notification.getId());
        response.setMessage(notification.getMessage());
        response.setType(notification.getType());
        response.setReferenceId(notification.getReferenceId());
        response.setReferenceType(notification.getReferenceType());
        response.setRead(notification.isRead());
        response.setCreatedAt(notification.getCreatedAt());
        return response;
    }
}
