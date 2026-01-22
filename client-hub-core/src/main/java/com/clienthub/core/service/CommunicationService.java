package com.clienthub.core.service;

import com.clienthub.common.context.TenantContext;
import com.clienthub.core.aop.LogAudit;
import com.clienthub.core.domain.entity.*;
import com.clienthub.core.domain.enums.*;
import com.clienthub.core.exception.ResourceNotFoundException;
import com.clienthub.core.exception.TaskNotFoundException;
import com.clienthub.core.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@Transactional
public class CommunicationService {

    private static final Logger logger = LoggerFactory.getLogger(CommunicationService.class);

    private final CommunicationThreadRepository threadRepository;
    private final CommentRepository commentRepository;
    private final NotificationRepository notificationRepository;

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;

    public CommunicationService(CommunicationThreadRepository threadRepository,
                                CommentRepository commentRepository,
                                NotificationRepository notificationRepository,
                                ProjectRepository projectRepository,
                                TaskRepository taskRepository,
                                InvoiceRepository invoiceRepository,
                                UserRepository userRepository) {
        this.threadRepository = threadRepository;
        this.commentRepository = commentRepository;
        this.notificationRepository = notificationRepository;
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.invoiceRepository = invoiceRepository;
        this.userRepository = userRepository;
    }

    private String getValidatedTenantId() {
        String tenantId = TenantContext.getTenantId();
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalStateException("Tenant context required");
        }
        return tenantId;
    }

    @LogAudit(action = AuditAction.CREATE, entityType = "COMMENT", entityId = "#result.id")
    public Comment postComment(CommentTargetType targetType, String targetId, String content, UUID authorId) {
        String tenantId = getValidatedTenantId();
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        User recipientForNotification = validateAccessAndGetRecipient(targetType, targetId, tenantId, authorId);

        CommunicationThread thread = threadRepository
                .findByTargetTypeAndTargetIdAndTenantId(targetType, targetId, tenantId)
                .orElseGet(() -> createThread(targetType, targetId, tenantId, author, "General Discussion"));

        Comment comment = new Comment();
        comment.setTenantId(tenantId);
        comment.setContent(content);
        comment.setAuthor(author);
        comment.setThread(thread);

        Comment savedComment = commentRepository.save(comment);

        if (recipientForNotification != null && !recipientForNotification.getId().equals(authorId)) {
            sendNotification(recipientForNotification, author, targetType, targetId, tenantId);
        }

        return savedComment;
    }

    @Transactional(readOnly = true)
    public Page<Comment> getComments(CommentTargetType targetType, String targetId, Pageable pageable, UUID userId) {
        String tenantId = getValidatedTenantId();
        validateAccessAndGetRecipient(targetType, targetId, tenantId, userId);

        return threadRepository.findByTargetTypeAndTargetIdAndTenantId(targetType, targetId, tenantId)
                .map(thread -> commentRepository.findByThreadIdAndTenantId(thread.getId(), tenantId, pageable))
                .orElse(Page.empty());
    }

    @LogAudit(action = AuditAction.UPDATE, entityType = "COMMENT", entityId = "#commentId")
    public Comment updateComment(Long commentId, String newContent, UUID userId) {
        String tenantId = getValidatedTenantId();

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
        String tenantId = getValidatedTenantId();

        Comment comment = commentRepository.findById(commentId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

        User user = userRepository.findById(userId)
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

    private User validateAccessAndGetRecipient(CommentTargetType targetType, String targetId, String tenantId, UUID userId) {
        switch (targetType) {
            case PROJECT:
                return validateProjectAccess(UUID.fromString(targetId), tenantId, userId);
            case TASK:
                return validateTaskAccess(UUID.fromString(targetId), tenantId, userId);
            case INVOICE:
                return validateInvoiceAccess(Long.parseLong(targetId), tenantId, userId);
            default:
                throw new IllegalArgumentException("Unknown target type");
        }
    }

    private User validateProjectAccess(UUID projectId, String tenantId, UUID userId) {
        Project project = projectRepository.findByIdAndTenantId(projectId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        boolean isOwner = project.getOwner().getId().equals(userId);

        boolean isWorkingOnProject = taskRepository.findByProjectIdAndTenantId(projectId, tenantId, Pageable.unpaged())
                .stream()
                .anyMatch(t -> t.getAssignedTo() != null && t.getAssignedTo().getId().equals(userId));

        if (!isOwner && !isWorkingOnProject) {
            User user = userRepository.findById(userId).orElseThrow();
            if (user.getRole() != Role.ADMIN) {
                throw new AccessDeniedException("You are not a member of this project (Owner or Active Task Assignee)");
            }
        }

        return isOwner ? null : project.getOwner();
    }

    private User validateTaskAccess(UUID taskId, String tenantId, UUID userId) {
        Task task = taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new TaskNotFoundException(taskId, tenantId));

        User assignee = task.getAssignedTo();
        User projectOwner = task.getProject().getOwner();

        boolean isAssignee = assignee != null && assignee.getId().equals(userId);
        boolean isOwner = projectOwner.getId().equals(userId);

        if (!isAssignee && !isOwner) {
            User user = userRepository.findById(userId).orElseThrow();
            if (user.getRole() != Role.ADMIN) {
                throw new AccessDeniedException("Only the assignee or project owner can comment on this task");
            }
        }

        if (isOwner) return assignee;
        return projectOwner;
    }

    private User validateInvoiceAccess(Long invoiceId, String tenantId, UUID userId) {
        Invoice invoice = invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        User client = invoice.getClient();
        User freelancer = invoice.getFreelancer();

        boolean isClient = client.getId().equals(userId);
        boolean isFreelancer = freelancer.getId().equals(userId);

        if (!isClient && !isFreelancer) {
            User user = userRepository.findById(userId).orElseThrow();
            if (user.getRole() != Role.ADMIN) {
                throw new AccessDeniedException("Access denied to invoice comments");
            }
        }

        if (isClient) return freelancer;
        return client;
    }

    private void sendNotification(User recipient, User sender, CommentTargetType type, String targetId, String tenantId) {
        if (recipient == null) return;

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