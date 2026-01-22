package com.clienthub.core.domain.entity;

import com.clienthub.common.domain.BaseEntity;
import com.clienthub.core.domain.enums.NotificationType;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notif_recipient", columnList = "recipient_id, is_read"),
        @Index(name = "idx_notif_tenant", columnList = "tenant_id")
})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Column(nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(name = "reference_id")
    private String referenceId;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public User getRecipient() { return recipient; }
    public void setRecipient(User recipient) { this.recipient = recipient; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }

    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }

    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
}