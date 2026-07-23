package com.clienthub.application.service;

import com.clienthub.application.dto.project.ProjectActivityResponse;
import com.clienthub.application.dto.project.ProjectFileResponse;
import com.clienthub.application.dto.audit.UserAuditProofResponse;
import com.clienthub.application.exception.ResourceNotFoundException;
import com.clienthub.common.service.TenantAwareService;
import com.clienthub.domain.entity.AuditLog;
import com.clienthub.domain.entity.Attachment;
import com.clienthub.domain.entity.Comment;
import com.clienthub.domain.entity.CommunicationThread;
import com.clienthub.domain.entity.Project;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.AuditAction;
import com.clienthub.domain.enums.CommentTargetType;
import com.clienthub.domain.enums.Role;
import com.clienthub.domain.repository.AuditLogRepository;
import com.clienthub.domain.repository.CommentRepository;
import com.clienthub.domain.repository.InvoiceRepository;
import com.clienthub.domain.repository.ProjectMemberRepository;
import com.clienthub.domain.repository.ProjectRepository;
import com.clienthub.domain.repository.TaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProjectPortalService extends TenantAwareService {

    private static final List<String> EMPTY_TARGET_SENTINEL = List.of("__clienthub_no_related_target__");

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TaskRepository taskRepository;
    private final InvoiceRepository invoiceRepository;
    private final CommentRepository commentRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditProofReader auditProofReader;
    private final AttachmentService attachmentService;

    public ProjectPortalService(ProjectRepository projectRepository,
                                ProjectMemberRepository projectMemberRepository,
                                TaskRepository taskRepository,
                                InvoiceRepository invoiceRepository,
                                CommentRepository commentRepository,
                                AuditLogRepository auditLogRepository,
                                AuditProofReader auditProofReader,
                                AttachmentService attachmentService) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.taskRepository = taskRepository;
        this.invoiceRepository = invoiceRepository;
        this.commentRepository = commentRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditProofReader = auditProofReader;
        this.attachmentService = attachmentService;
    }

    public List<ProjectFileResponse> getProjectFiles(UUID projectId, UUID currentUserId, Role callerRole) {
        String tenantId = getCurrentTenantId();
        validateProjectAccess(projectId, currentUserId, callerRole, tenantId);

        TargetIndex targetIndex = loadTargetIndex(projectId, tenantId);
        List<Comment> comments = findProjectScopedComments(projectId, tenantId, targetIndex);

        List<ProjectFileResponse> files = new ArrayList<>();
        for (Comment comment : comments) {
            List<String> attachmentUrls = comment.getAttachmentUrls();
            if (attachmentUrls == null || attachmentUrls.isEmpty()) {
                continue;
            }

            CommunicationThread thread = comment.getThread();
            CommentTargetType sourceTargetType =
                    thread != null && thread.getTargetType() != null
                            ? thread.getTargetType()
                            : CommentTargetType.PROJECT;
            String sourceType = sourceTargetType.name();
            String sourceId = thread != null ? thread.getTargetId() : projectId.toString();
            String authorName = resolveAuthorName(comment.getAuthor());

            for (String fileUrl : attachmentUrls) {
                if (fileUrl == null || fileUrl.isBlank()) {
                    continue;
                }
                attachmentService.findAuthorizedProtectedReference(
                                fileUrl, sourceTargetType, sourceId, currentUserId)
                        .ifPresent(attachment -> files.add(toProjectFile(
                                attachment, comment, sourceType, sourceId, authorName)));
            }
        }

        return files;
    }

    private ProjectFileResponse toProjectFile(Attachment attachment,
                                              Comment comment,
                                              String sourceType,
                                              String sourceId,
                                              String authorName) {
        return new ProjectFileResponse(
                "/api/attachments/" + attachment.getId(),
                attachment.getOriginalFilename(),
                sourceType,
                sourceId,
                comment.getId(),
                authorName,
                comment.getCreatedAt()
        );
    }

    public Page<ProjectActivityResponse> getProjectActivity(UUID projectId, UUID currentUserId, Role callerRole, Pageable pageable) {
        String tenantId = getCurrentTenantId();
        validateProjectAccess(projectId, currentUserId, callerRole, tenantId);

        TargetIndex targetIndex = loadTargetIndex(projectId, tenantId);
        List<String> commentIds = findProjectScopedComments(projectId, tenantId, targetIndex)
                .stream()
                .map(Comment::getId)
                .filter(id -> id != null)
                .map(String::valueOf)
                .toList();

        Page<AuditLog> logs = auditLogRepository.findProjectActivity(
                tenantId,
                projectId.toString(),
                nonEmpty(targetIndex.taskIds()),
                nonEmpty(targetIndex.invoiceIds()),
                nonEmpty(commentIds),
                pageable
        );
        Map<Long, UserAuditProofResponse> proofs = auditProofReader.getUserProofs(
                logs.getContent().stream().map(AuditLog::getId).toList());
        return logs.map(log -> toActivityResponse(log, log.getId() != null ? proofs.get(log.getId()) : null));
    }

    public UserAuditProofResponse getActivityProof(UUID projectId, long auditLogId, UUID currentUserId,
                                                    Role callerRole, boolean verify) {
        String tenantId = getCurrentTenantId();
        validateProjectAccess(projectId, currentUserId, callerRole, tenantId);
        AuditLog log = auditLogRepository.findById(auditLogId)
                .filter(value -> tenantId.equals(value.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("AuditLog", "id", auditLogId));

        TargetIndex targets = loadTargetIndex(projectId, tenantId);
        Set<String> commentIds = findProjectScopedComments(projectId, tenantId, targets).stream()
                .map(Comment::getId)
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.toSet());
        if (!belongsToProject(log, projectId, targets, commentIds)) {
            throw new ResourceNotFoundException("AuditLog", "id", auditLogId);
        }

        return verify ? auditProofReader.verifyUserProof(auditLogId) : auditProofReader.getUserProof(auditLogId);
    }

    private boolean belongsToProject(AuditLog log, UUID projectId, TargetIndex targets, Set<String> commentIds) {
        return switch (normalize(log.getEntityType())) {
            case "PROJECT" -> projectId.toString().equals(log.getEntityId());
            case "TASK" -> targets.taskIds().contains(log.getEntityId());
            case "INVOICE" -> targets.invoiceIds().contains(log.getEntityId());
            case "COMMENT" -> commentIds.contains(log.getEntityId());
            default -> false;
        };
    }

    private Project validateProjectAccess(UUID projectId, UUID currentUserId, Role callerRole, String tenantId) {
        Project project = projectRepository.findByIdAndTenantId(projectId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        boolean isMemberFreelancer = callerRole == Role.FREELANCER
                && projectMemberRepository.existsByIdProjectIdAndIdUserIdAndTenantId(
                        projectId,
                        currentUserId,
                        tenantId
                );
        ProjectAccessPolicy.requireReadAccess(
                project,
                currentUserId,
                callerRole,
                isMemberFreelancer,
                "You are not allowed to view this project portal");

        return project;
    }

    private TargetIndex loadTargetIndex(UUID projectId, String tenantId) {
        List<String> taskIds = taskRepository.findIdsByProjectIdAndTenantId(projectId, tenantId)
                .stream()
                .map(UUID::toString)
                .toList();

        List<String> invoiceIds = invoiceRepository.findIdsByProjectIdAndTenantId(projectId, tenantId)
                .stream()
                .map(String::valueOf)
                .toList();

        return new TargetIndex(taskIds, invoiceIds);
    }

    private List<Comment> findProjectScopedComments(UUID projectId, String tenantId, TargetIndex targetIndex) {
        return commentRepository.findProjectScopedComments(
                tenantId,
                projectId.toString(),
                nonEmpty(targetIndex.taskIds()),
                nonEmpty(targetIndex.invoiceIds())
        );
    }

    private List<String> nonEmpty(List<String> values) {
        return values == null || values.isEmpty() ? EMPTY_TARGET_SENTINEL : values;
    }

    private ProjectActivityResponse toActivityResponse(AuditLog log, UserAuditProofResponse proof) {
        UserAuditProofResponse effectiveProof = proof != null ? proof : UserAuditProofResponse.notAvailable();
        return new ProjectActivityResponse(
                log.getId(),
                log.getAction() != null ? log.getAction().name() : "UPDATE",
                buildActivityLabel(log),
                log.getEntityType(),
                log.getEntityId(),
                resolveActorName(log),
                log.getCreatedAt(),
                effectiveProof.verificationStatus(),
                effectiveProof.proofAvailable(),
                effectiveProof.anchoredAt()
        );
    }

    private String buildActivityLabel(AuditLog log) {
        String entityLabel = switch (normalize(log.getEntityType())) {
            case "PROJECT" -> "Project";
            case "TASK" -> "Task";
            case "INVOICE" -> "Invoice";
            case "COMMENT" -> "Message";
            default -> "Project item";
        };

        AuditAction action = log.getAction();
        if ("COMMENT".equalsIgnoreCase(log.getEntityType())) {
            return switch (action) {
                case CREATE -> "Message added";
                case UPDATE -> "Message updated";
                case DELETE -> "Message deleted";
                default -> "Message activity";
            };
        }

        if ("INVOICE".equalsIgnoreCase(log.getEntityType())) {
            return switch (action) {
                case INVOICE_SENT -> "Invoice sent";
                case INVOICE_PAID -> "Invoice paid";
                case INVOICE_CANCELLED -> "Invoice cancelled";
                case CREATE -> "Invoice created";
                case UPDATE -> "Invoice updated";
                case DELETE -> "Invoice deleted";
                default -> "Invoice activity";
            };
        }

        return switch (action) {
            case CREATE -> entityLabel + " created";
            case UPDATE -> entityLabel + " updated";
            case DELETE -> entityLabel + " deleted";
            default -> entityLabel + " activity";
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }

    private String resolveActorName(AuditLog log) {
        if (log.getUserEmail() != null && !log.getUserEmail().isBlank()) {
            return log.getUserEmail();
        }

        if (log.getUserRole() != null && !log.getUserRole().isBlank()) {
            return log.getUserRole();
        }

        return "System";
    }

    private String resolveAuthorName(User author) {
        if (author == null) {
            return "Unknown";
        }

        if (author.getFullName() != null && !author.getFullName().isBlank()) {
            return author.getFullName();
        }

        if (author.getEmail() != null && !author.getEmail().isBlank()) {
            return author.getEmail();
        }

        return "Unknown";
    }

    private record TargetIndex(List<String> taskIds, List<String> invoiceIds) {
    }
}
