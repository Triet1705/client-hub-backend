package com.clienthub.core.domain.entity;

import com.clienthub.common.domain.BaseEntity;
import com.clienthub.core.domain.enums.CommentTargetType;
import com.clienthub.core.domain.enums.ThreadStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "communication_threads", indexes = {
        @Index(name = "idx_thread_tenant", columnList = "tenant_id"),
        @Index(name = "idx_thread_target", columnList = "target_type, target_id"),
        @Index(name = "idx_thread_status", columnList = "status")
})
@SQLDelete(sql = "UPDATE communication_threads SET is_deleted = true, deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class CommunicationThread extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @NotBlank(message = "Thread topic is required")
    @Column(nullable = false, length = 200)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ThreadStatus status = ThreadStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 50)
    private CommentTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private String targetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "is_deleted")
    private boolean isDeleted = false;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public ThreadStatus getStatus() { return status; }
    public void setStatus(ThreadStatus status) { this.status = status; }
    public CommentTargetType getTargetType() { return targetType; }
    public void setTargetType(CommentTargetType targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public User getAuthor() { return author; }
    public void setAuthor(User author) { this.author = author; }
    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }
}