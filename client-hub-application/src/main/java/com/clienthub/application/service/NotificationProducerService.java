package com.clienthub.application.service;

import com.clienthub.domain.entity.Invoice;
import com.clienthub.domain.entity.Notification;
import com.clienthub.domain.entity.Project;
import com.clienthub.domain.entity.ProjectMember;
import com.clienthub.domain.entity.Task;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.NotificationType;
import com.clienthub.domain.repository.NotificationRepository;
import com.clienthub.domain.repository.ProjectMemberRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class NotificationProducerService {

    private final NotificationRepository notificationRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserService userService;

    public NotificationProducerService(NotificationRepository notificationRepository,
                                       ProjectMemberRepository projectMemberRepository,
                                       UserService userService) {
        this.notificationRepository = notificationRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.userService = userService;
    }

    public void notifyTaskCompleted(Task task) {
        Project project = task.getProject();
        User owner = project.getOwner();

        createNotification(
                owner,
                NotificationType.TASK_COMPLETED,
                task.getId().toString(),
                "TASK",
                String.format("Task '%s' was marked as completed", task.getTitle()),
                task.getTenantId()
        );
    }

    public void notifyProjectCompleted(Project project) {
        String tenantId = project.getTenantId();
        Set<UUID> notifiedUserIds = new HashSet<>();

        User owner = project.getOwner();
        createNotification(
                owner,
                NotificationType.PROJECT_COMPLETED,
                project.getId().toString(),
                "PROJECT",
                String.format("Project '%s' is now completed", project.getTitle()),
                tenantId
        );
        if (owner != null) {
            notifiedUserIds.add(owner.getId());
        }

        List<ProjectMember> members = projectMemberRepository.findByIdProjectIdAndTenantId(project.getId(), tenantId);
        for (ProjectMember member : members) {
            User recipient = member.getUser();
            if (recipient != null && !notifiedUserIds.contains(recipient.getId())) {
                createNotification(
                        recipient,
                        NotificationType.PROJECT_COMPLETED,
                        project.getId().toString(),
                        "PROJECT",
                        String.format("Project '%s' is now completed", project.getTitle()),
                        tenantId
                );
                notifiedUserIds.add(recipient.getId());
            }
        }
    }

    public void notifyInvoicePaid(Invoice invoice) {
        User freelancer = invoice.getFreelancer();

        createNotification(
                freelancer,
                NotificationType.INVOICE_PAID,
                invoice.getId().toString(),
                "INVOICE",
                String.format("Invoice #%d has been marked as PAID", invoice.getId()),
                invoice.getTenantId()
        );
    }

    private void createNotification(User recipient,
                                    NotificationType type,
                                    String referenceId,
                                    String referenceType,
                                    String message,
                                    String tenantId) {
        if (recipient == null) {
            return;
        }
        if (!userService.allowsNotification(recipient.getId(), tenantId, type)) {
            return;
        }

        Notification notification = new Notification();
        notification.setTenantId(tenantId);
        notification.setRecipient(recipient);
        notification.setType(type);
        notification.setReferenceId(referenceId);
        notification.setReferenceType(referenceType);
        notification.setMessage(message);

        notificationRepository.save(notification);
    }
}
