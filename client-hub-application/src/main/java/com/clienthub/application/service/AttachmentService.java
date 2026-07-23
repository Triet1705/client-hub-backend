package com.clienthub.application.service;

import com.clienthub.application.dto.AttachmentDownload;
import com.clienthub.application.dto.AttachmentResponseDto;
import com.clienthub.application.exception.ResourceNotFoundException;
import com.clienthub.common.service.TenantAwareService;
import com.clienthub.domain.entity.Attachment;
import com.clienthub.domain.enums.CommentTargetType;
import com.clienthub.domain.repository.AttachmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AttachmentService extends TenantAwareService {

    private static final Logger logger = LoggerFactory.getLogger(AttachmentService.class);
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );
    private static final Pattern PROTECTED_URL =
            Pattern.compile("^/api/attachments/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$");

    private final AttachmentRepository attachmentRepository;
    private final TargetAccessService targetAccessService;
    private final Path uploadRoot;

    public AttachmentService(AttachmentRepository attachmentRepository,
                             TargetAccessService targetAccessService,
                             @Value("${file.upload-dir:./uploads/attachments}") String uploadDir) {
        this.attachmentRepository = attachmentRepository;
        this.targetAccessService = targetAccessService;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Transactional
    public AttachmentResponseDto uploadDocument(CommentTargetType targetType,
                                                String targetId,
                                                MultipartFile file,
                                                UUID actorId) {
        TargetAccessService.AuthorizedTarget authorizedTarget =
                targetAccessService.authorize(targetType, targetId, actorId);
        ValidatedFile validatedFile = validateFile(file);
        String tenantId = getCurrentTenantId();
        String storageKey = UUID.randomUUID().toString();
        Path destination = resolveStoragePath(storageKey);

        try {
            Files.createDirectories(uploadRoot);
            Files.copy(file.getInputStream(), destination);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to store attachment", exception);
        }

        registerRollbackCleanup(destination);

        Attachment attachment = new Attachment();
        attachment.setTenantId(tenantId);
        attachment.setUploader(authorizedTarget.actor());
        attachment.setTargetType(authorizedTarget.targetType());
        attachment.setTargetId(authorizedTarget.targetId());
        attachment.setStorageKey(storageKey);
        attachment.setOriginalFilename(validatedFile.originalFilename());
        attachment.setMediaType(validatedFile.mediaType());
        attachment.setSizeBytes(file.getSize());

        try {
            Attachment saved = attachmentRepository.saveAndFlush(attachment);
            return toResponse(saved);
        } catch (RuntimeException exception) {
            deleteCompensatingFile(destination);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public AttachmentDownload getDownload(UUID attachmentId, UUID actorId) {
        String tenantId = getCurrentTenantId();
        Attachment attachment = attachmentRepository.findByIdAndTenantId(attachmentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", "id", attachmentId));

        targetAccessService.authorize(
                attachment.getTargetType(),
                attachment.getTargetId(),
                actorId);

        Path path = resolveStoragePath(attachment.getStorageKey());
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new ResourceNotFoundException("Attachment", "id", attachmentId);
        }

        return new AttachmentDownload(
                path,
                attachment.getOriginalFilename(),
                attachment.getMediaType(),
                attachment.getSizeBytes());
    }

    @Transactional(readOnly = true)
    public void validateCommentAttachmentReferences(List<String> attachmentUrls,
                                                    CommentTargetType targetType,
                                                    String targetId) {
        if (attachmentUrls == null || attachmentUrls.isEmpty()) {
            return;
        }

        String tenantId = getCurrentTenantId();
        for (String attachmentUrl : attachmentUrls) {
            Attachment attachment = findProtectedReference(attachmentUrl, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Comment attachments must use a protected attachment reference"));
            if (attachment.getTargetType() != targetType || !attachment.getTargetId().equals(targetId)) {
                throw new IllegalArgumentException("Attachment target does not match the discussion target");
            }
        }
    }

    @Transactional(readOnly = true)
    public Optional<Attachment> findProtectedReference(String attachmentUrl,
                                                       CommentTargetType targetType,
                                                       String targetId) {
        return findProtectedReference(attachmentUrl, getCurrentTenantId())
                .filter(attachment -> attachment.getTargetType() == targetType)
                .filter(attachment -> attachment.getTargetId().equals(targetId));
    }

    @Transactional(readOnly = true)
    public Optional<Attachment> findAuthorizedProtectedReference(String attachmentUrl,
                                                                 CommentTargetType targetType,
                                                                 String targetId,
                                                                 UUID actorId) {
        Optional<Attachment> attachment =
                findProtectedReference(attachmentUrl, targetType, targetId);
        if (attachment.isEmpty()) {
            return Optional.empty();
        }
        try {
            targetAccessService.authorize(targetType, targetId, actorId);
            return attachment;
        } catch (AccessDeniedException | ResourceNotFoundException exception) {
            return Optional.empty();
        }
    }

    private Optional<Attachment> findProtectedReference(String attachmentUrl, String tenantId) {
        if (attachmentUrl == null) {
            return Optional.empty();
        }
        Matcher matcher = PROTECTED_URL.matcher(attachmentUrl);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            return attachmentRepository.findByIdAndTenantId(UUID.fromString(matcher.group(1)), tenantId);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private AttachmentResponseDto toResponse(Attachment attachment) {
        return new AttachmentResponseDto(
                attachment.getId(),
                protectedUrl(attachment.getId()),
                attachment.getOriginalFilename(),
                attachment.getMediaType(),
                attachment.getSizeBytes(),
                attachment.getCreatedAt());
    }

    private String protectedUrl(UUID attachmentId) {
        return "/api/attachments/" + attachmentId;
    }

    private ValidatedFile validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Failed to store empty file.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds limit of 5MB.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("File type not allowed.");
        }

        String suppliedName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        String originalFilename = StringUtils.cleanPath(suppliedName);
        if (originalFilename.isBlank()
                || originalFilename.contains("..")
                || originalFilename.contains("/")
                || originalFilename.contains("\\")) {
            throw new IllegalArgumentException("Filename contains an invalid path sequence");
        }
        if (originalFilename.length() > 255) {
            throw new IllegalArgumentException("Filename is too long");
        }
        return new ValidatedFile(originalFilename, contentType);
    }

    private Path resolveStoragePath(String storageKey) {
        UUID parsedStorageKey;
        try {
            parsedStorageKey = UUID.fromString(storageKey);
        } catch (IllegalArgumentException exception) {
            throw new SecurityException("Invalid attachment storage key", exception);
        }

        Path path = uploadRoot.resolve(parsedStorageKey.toString()).normalize();
        if (!path.startsWith(uploadRoot) || !uploadRoot.equals(path.getParent())) {
            throw new SecurityException("Cannot access attachment outside the upload directory");
        }
        return path;
    }

    private void registerRollbackCleanup(Path destination) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deleteCompensatingFile(destination);
                }
            }
        });
    }

    private void deleteCompensatingFile(Path destination) {
        try {
            Files.deleteIfExists(destination);
        } catch (IOException cleanupException) {
            logger.error("Failed to remove attachment after metadata persistence failure: {}",
                    destination.getFileName(), cleanupException);
        }
    }

    private record ValidatedFile(String originalFilename, String mediaType) {
    }
}
