package com.clienthub.application.service;

import com.clienthub.application.exception.ResourceNotFoundException;
import com.clienthub.common.service.TenantAwareService;
import com.clienthub.domain.entity.Invoice;
import com.clienthub.domain.entity.Project;
import com.clienthub.domain.entity.Task;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.CommentTargetType;
import com.clienthub.domain.enums.Role;
import com.clienthub.domain.repository.InvoiceRepository;
import com.clienthub.domain.repository.ProjectMemberRepository;
import com.clienthub.domain.repository.ProjectRepository;
import com.clienthub.domain.repository.TaskRepository;
import com.clienthub.domain.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TargetAccessService extends TenantAwareService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TaskRepository taskRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;

    public TargetAccessService(ProjectRepository projectRepository,
                               ProjectMemberRepository projectMemberRepository,
                               TaskRepository taskRepository,
                               InvoiceRepository invoiceRepository,
                               UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.taskRepository = taskRepository;
        this.invoiceRepository = invoiceRepository;
        this.userRepository = userRepository;
    }

    public AuthorizedTarget authorize(CommentTargetType targetType, String targetId, UUID actorId) {
        if (targetType == null) {
            throw new IllegalArgumentException("Target type is required");
        }
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("Target ID is required");
        }

        return switch (targetType) {
            case PROJECT -> authorizeProject(parseUuid(targetId, "project"), actorId);
            case TASK -> authorizeTask(parseUuid(targetId, "task"), actorId);
            case INVOICE -> authorizeInvoice(parseLong(targetId, "invoice"), actorId);
        };
    }

    private AuthorizedTarget authorizeProject(UUID projectId, UUID actorId) {
        String tenantId = getCurrentTenantId();
        Project project = projectRepository.findByIdAndTenantId(projectId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
        User actor = loadActor(actorId, tenantId);

        boolean isAdmin = actor.getRole() == Role.ADMIN;
        boolean isOwningClient = actor.getRole() == Role.CLIENT
                && project.getOwner() != null
                && actorId.equals(project.getOwner().getId());
        boolean isMemberFreelancer = actor.getRole() == Role.FREELANCER
                && projectMemberRepository.existsByIdProjectIdAndIdUserIdAndTenantId(
                        projectId, actorId, tenantId);

        if (!isAdmin && !isOwningClient && !isMemberFreelancer) {
            throw new AccessDeniedException("You are not allowed to access this project");
        }

        User recipient = isOwningClient ? null : project.getOwner();
        return new AuthorizedTarget(CommentTargetType.PROJECT, projectId.toString(), actor, recipient);
    }

    private AuthorizedTarget authorizeTask(UUID taskId, UUID actorId) {
        String tenantId = getCurrentTenantId();
        Task task = taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", taskId));
        User actor = loadActor(actorId, tenantId);

        User owner = task.getProject() != null ? task.getProject().getOwner() : null;
        User assignee = task.getAssignedTo();
        boolean isAdmin = actor.getRole() == Role.ADMIN;
        boolean isOwningClient = actor.getRole() == Role.CLIENT
                && owner != null
                && actorId.equals(owner.getId());
        boolean isAssignedFreelancer = actor.getRole() == Role.FREELANCER
                && assignee != null
                && actorId.equals(assignee.getId());

        if (!isAdmin && !isOwningClient && !isAssignedFreelancer) {
            throw new AccessDeniedException("Only the project owner, task assignee, or Administrator can access this task");
        }

        User recipient = isOwningClient ? assignee : owner;
        return new AuthorizedTarget(CommentTargetType.TASK, taskId.toString(), actor, recipient);
    }

    private AuthorizedTarget authorizeInvoice(Long invoiceId, UUID actorId) {
        String tenantId = getCurrentTenantId();
        Invoice invoice = invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId));
        User actor = loadActor(actorId, tenantId);

        boolean isAdmin = actor.getRole() == Role.ADMIN;
        boolean isInvoiceClient = actor.getRole() == Role.CLIENT
                && invoice.getClient() != null
                && actorId.equals(invoice.getClient().getId());
        boolean isInvoiceFreelancer = actor.getRole() == Role.FREELANCER
                && invoice.getFreelancer() != null
                && actorId.equals(invoice.getFreelancer().getId());

        if (!isAdmin && !isInvoiceClient && !isInvoiceFreelancer) {
            throw new AccessDeniedException("You are not allowed to access this invoice");
        }

        User recipient = isInvoiceClient ? invoice.getFreelancer() : invoice.getClient();
        return new AuthorizedTarget(CommentTargetType.INVOICE, invoiceId.toString(), actor, recipient);
    }

    private User loadActor(UUID actorId, String tenantId) {
        return userRepository.findByIdAndTenantId(actorId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", actorId));
    }

    private UUID parseUuid(String value, String label) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid " + label + " target ID", exception);
        }
    }

    private Long parseLong(String value, String label) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid " + label + " target ID", exception);
        }
    }

    public record AuthorizedTarget(
            CommentTargetType targetType,
            String targetId,
            User actor,
            User notificationRecipient) {
    }
}
