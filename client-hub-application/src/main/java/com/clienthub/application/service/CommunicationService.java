package com.clienthub.application.service;

import com.clienthub.common.service.TenantAwareService;
import com.clienthub.application.aop.LogAudit;
import com.clienthub.application.exception.ResourceNotFoundException;
import com.clienthub.application.exception.TaskNotFoundException;
import com.clienthub.domain.entity.*;
import com.clienthub.domain.enums.*;
import com.clienthub.domain.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class CommunicationService extends TenantAwareService {

    private static final Logger logger = LoggerFactory.getLogger(CommunicationService.class);

    private final CommunicationThreadRepository threadRepository;
    private final CommentRepository commentRepository;
    private final NotificationRepository notificationRepository;

    private final UserRepository userRepository;
    private final UserService userService;
    private final TargetAccessService targetAccessService;
    private final AttachmentService attachmentService;

    public CommunicationService(CommunicationThreadRepository threadRepository,
                                CommentRepository commentRepository,
                                NotificationRepository notificationRepository,
                                UserRepository userRepository,
                                UserService userService,
                                TargetAccessService targetAccessService,
                                AttachmentService attachmentService) {
        this.threadRepository = threadRepository;
        this.commentRepository = commentRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.targetAccessService = targetAccessService;
        this.attachmentService = attachmentService;
    }

    @LogAudit(action = AuditAction.CREATE, entityType = "COMMENT", entityId = "#result.id")
    public Comment postComment(CommentTargetType targetType, String targetId, String content, UUID authorId, java.util.List<String> attachmentUrls) {
        String tenantId = getCurrentTenantId();
        User author = userRepository.findByIdAndTenantId(authorId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        TargetAccessService.AuthorizedTarget authorizedTarget =
                targetAccessService.authorize(targetType, targetId, authorId);
        attachmentService.validateCommentAttachmentReferences(
                attachmentUrls, targetType, authorizedTarget.targetId());

        CommunicationThread thread = threadRepository
                .findByTargetTypeAndTargetIdAndTenantId(targetType, authorizedTarget.targetId(), tenantId)
                .orElseGet(() -> createThread(
                        targetType, authorizedTarget.targetId(), tenantId, author, "General Discussion"));

        Comment comment = new Comment();
        comment.setTenantId(tenantId);
        comment.setContent(content);
        comment.setAuthor(author);
        comment.setThread(thread);
        if (attachmentUrls != null) {
            comment.setAttachmentUrls(attachmentUrls);
        }

        Comment savedComment = commentRepository.save(comment);

        User recipientForNotification = authorizedTarget.notificationRecipient();
        if (recipientForNotification != null && !recipientForNotification.getId().equals(authorId)) {
            sendNotification(
                    recipientForNotification, author, targetType, authorizedTarget.targetId(), tenantId);
        }

        return savedComment;
    }

    @Transactional(readOnly = true)
    public Page<Comment> getComments(CommentTargetType targetType, String targetId, Pageable pageable, UUID userId) {
        String tenantId = getCurrentTenantId();
        TargetAccessService.AuthorizedTarget authorizedTarget =
                targetAccessService.authorize(targetType, targetId, userId);

        return threadRepository.findByTargetTypeAndTargetIdAndTenantId(
                        targetType, authorizedTarget.targetId(), tenantId)
                .map(thread -> commentRepository.findByThreadIdAndTenantId(thread.getId(), tenantId, pageable))
                .orElse(Page.empty());
    }

    @LogAudit(action = AuditAction.UPDATE, entityType = "COMMENT", entityId = "#commentId")
    public Comment updateComment(Long commentId, String newContent, UUID userId) {
        String tenantId = getCurrentTenantId();

        Comment comment = commentRepository.findById(commentId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

        if (!comment.getAuthor().getId().equals(userId)) {
            throw new AccessDeniedException("You can only edit your own comments");
        }

        if (comment.isDeleted()) {
            throw new IllegalStateException("Cannot edit a deleted comment");
        }

        comment.setContent(newContent);
        return commentRepository.save(comment);
    }

    @LogAudit(action = AuditAction.DELETE, entityType = "COMMENT", entityId = "#commentId")
    public void deleteComment(Long commentId, UUID userId) {
        String tenantId = getCurrentTenantId();

        Comment comment = commentRepository.findById(commentId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean isAuthor = comment.getAuthor().getId().equals(userId);
        boolean isAdmin = user.getRole() == Role.ADMIN;

        if (!isAuthor && !isAdmin) {
            throw new AccessDeniedException("You do not have permission to delete this comment");
        }

        commentRepository.delete(comment);
    }

    private CommunicationThread createThread(CommentTargetType targetType, String targetId,
                                             String tenantId, User author, String defaultTopic) {
        CommunicationThread thread = new CommunicationThread();
        thread.setTenantId(tenantId);
        thread.setTargetType(targetType);
        thread.setTargetId(targetId);
        thread.setTopic(defaultTopic);
        thread.setStatus(ThreadStatus.OPEN);
        thread.setAuthor(author);
        return threadRepository.save(thread);
    }

    private void sendNotification(User recipient, User sender, CommentTargetType type, String targetId, String tenantId) {
        if (recipient == null) return;
        if (!userService.allowsNotification(recipient.getId(), tenantId, NotificationType.NEW_COMMENT)) return;

        Notification notification = new Notification();
        notification.setTenantId(tenantId);
        notification.setRecipient(recipient);
        notification.setType(NotificationType.NEW_COMMENT);
        notification.setReferenceType(type.name());
        notification.setReferenceId(targetId);
        notification.setMessage(String.format("New comment from %s on %s #%s",
                sender.getFullName(), type.name().toLowerCase(), targetId));

        notificationRepository.save(notification);
    }
}
