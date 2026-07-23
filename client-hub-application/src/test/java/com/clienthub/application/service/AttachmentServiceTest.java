package com.clienthub.application.service;

import com.clienthub.application.dto.AttachmentDownload;
import com.clienthub.application.dto.AttachmentResponseDto;
import com.clienthub.application.exception.ResourceNotFoundException;
import com.clienthub.common.context.TenantContext;
import com.clienthub.domain.entity.Attachment;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.CommentTargetType;
import com.clienthub.domain.enums.Role;
import com.clienthub.domain.repository.AttachmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    private static final String TENANT_ID = "tenant-a";
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();

    @TempDir
    Path tempDir;
    @Mock
    private AttachmentRepository attachmentRepository;
    @Mock
    private TargetAccessService targetAccessService;

    private AttachmentService attachmentService;
    private User actor;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        attachmentService = new AttachmentService(
                attachmentRepository, targetAccessService, tempDir.toString());
        actor = User.builder()
                .id(ACTOR_ID)
                .tenantId(TENANT_ID)
                .email("actor@example.com")
                .password("password")
                .fullName("Actor")
                .role(Role.CLIENT)
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void authorizedUploadPersistsMetadataAndOpaqueStorageObject() throws IOException {
        byte[] bytes = "attachment".getBytes();
        MockMultipartFile file =
                new MockMultipartFile("file", "scope.pdf", "application/pdf", bytes);
        authorizeProject();
        when(attachmentRepository.saveAndFlush(any(Attachment.class))).thenAnswer(invocation -> {
            Attachment attachment = invocation.getArgument(0);
            attachment.setId(UUID.randomUUID());
            attachment.setCreatedAt(Instant.parse("2026-07-23T10:00:00Z"));
            return attachment;
        });

        AttachmentResponseDto response = attachmentService.uploadDocument(
                CommentTargetType.PROJECT, PROJECT_ID.toString(), file, ACTOR_ID);

        assertEquals("scope.pdf", response.getFileName());
        assertEquals("application/pdf", response.getFileType());
        assertEquals(bytes.length, response.getSizeBytes());
        assertEquals("/api/attachments/" + response.getId(), response.getFileUrl());
        List<Path> storedFiles;
        try (var files = Files.list(tempDir)) {
            storedFiles = files.toList();
        }
        assertEquals(1, storedFiles.size());
        assertEquals(36, storedFiles.getFirst().getFileName().toString().length());
        assertArrayEquals(bytes, Files.readAllBytes(storedFiles.getFirst()));
        verify(attachmentRepository).saveAndFlush(any(Attachment.class));
    }

    @Test
    void databaseFailureCompensatesByRemovingStoredObject() throws IOException {
        MockMultipartFile file =
                new MockMultipartFile("file", "scope.pdf", "application/pdf", "data".getBytes());
        authorizeProject();
        when(attachmentRepository.saveAndFlush(any(Attachment.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, () -> attachmentService.uploadDocument(
                CommentTargetType.PROJECT, PROJECT_ID.toString(), file, ACTOR_ID));

        try (var files = Files.list(tempDir)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void traversalFilenameIsRejectedBeforeStorageAndPersistence() {
        MockMultipartFile file =
                new MockMultipartFile("file", "../secret.pdf", "application/pdf", "data".getBytes());
        authorizeProject();

        assertThrows(IllegalArgumentException.class, () -> attachmentService.uploadDocument(
                CommentTargetType.PROJECT, PROJECT_ID.toString(), file, ACTOR_ID));

        verify(attachmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void oversizedAndInvalidMediaFilesAreRejected() {
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "large.pdf", "application/pdf", new byte[5 * 1024 * 1024 + 1]);
        MockMultipartFile invalid = new MockMultipartFile(
                "file", "script.js", "application/javascript", "alert(1)".getBytes());
        authorizeProject();

        assertThrows(IllegalArgumentException.class, () -> attachmentService.uploadDocument(
                CommentTargetType.PROJECT, PROJECT_ID.toString(), oversized, ACTOR_ID));
        assertThrows(IllegalArgumentException.class, () -> attachmentService.uploadDocument(
                CommentTargetType.PROJECT, PROJECT_ID.toString(), invalid, ACTOR_ID));
        verify(attachmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void authorizedDownloadUsesTenantMetadataThenTargetAuthorization() throws IOException {
        UUID attachmentId = UUID.randomUUID();
        String storageKey = UUID.randomUUID().toString();
        byte[] data = "protected".getBytes();
        Files.write(tempDir.resolve(storageKey), data);
        Attachment attachment = attachment(attachmentId, storageKey);
        when(attachmentRepository.findByIdAndTenantId(attachmentId, TENANT_ID))
                .thenReturn(Optional.of(attachment));

        AttachmentDownload download = attachmentService.getDownload(attachmentId, ACTOR_ID);

        assertEquals("scope.pdf", download.originalFilename());
        assertArrayEquals(data, Files.readAllBytes(download.path()));
        verify(targetAccessService).authorize(
                CommentTargetType.PROJECT, PROJECT_ID.toString(), ACTOR_ID);
    }

    @Test
    void sameTenantOutsiderCannotResolveStorageEvenWithKnownAttachmentId() {
        UUID attachmentId = UUID.randomUUID();
        Attachment attachment = attachment(attachmentId, UUID.randomUUID().toString());
        when(attachmentRepository.findByIdAndTenantId(attachmentId, TENANT_ID))
                .thenReturn(Optional.of(attachment));
        when(targetAccessService.authorize(
                CommentTargetType.PROJECT, PROJECT_ID.toString(), ACTOR_ID))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> attachmentService.getDownload(attachmentId, ACTOR_ID));
    }

    @Test
    void missingAndCrossTenantAttachmentIdsUseNonDisclosingNotFound() {
        UUID attachmentId = UUID.randomUUID();
        when(attachmentRepository.findByIdAndTenantId(attachmentId, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> attachmentService.getDownload(attachmentId, ACTOR_ID));
        verifyNoInteractions(targetAccessService);
    }

    @Test
    void metadataWithoutStorageObjectReturnsNotFoundAfterAuthorization() {
        UUID attachmentId = UUID.randomUUID();
        Attachment attachment = attachment(attachmentId, UUID.randomUUID().toString());
        when(attachmentRepository.findByIdAndTenantId(attachmentId, TENANT_ID))
                .thenReturn(Optional.of(attachment));

        assertThrows(ResourceNotFoundException.class,
                () -> attachmentService.getDownload(attachmentId, ACTOR_ID));
        verify(targetAccessService).authorize(
                CommentTargetType.PROJECT, PROJECT_ID.toString(), ACTOR_ID);
    }

    @Test
    void legacyUrlsCannotBeAttachedToNewCommentsOrResolvedAsProtectedReferences() {
        String legacyUrl = "/uploads/attachments/legacy.pdf";

        assertThrows(IllegalArgumentException.class,
                () -> attachmentService.validateCommentAttachmentReferences(
                        List.of(legacyUrl), CommentTargetType.PROJECT, PROJECT_ID.toString()));
        assertTrue(attachmentService.findProtectedReference(
                legacyUrl, CommentTargetType.PROJECT, PROJECT_ID.toString()).isEmpty());
        verifyNoInteractions(attachmentRepository);
    }

    @Test
    void protectedReferenceMustBelongToTheSameDiscussionTarget() {
        UUID attachmentId = UUID.randomUUID();
        Attachment attachment = attachment(attachmentId, UUID.randomUUID().toString());
        String url = "/api/attachments/" + attachmentId;
        when(attachmentRepository.findByIdAndTenantId(attachmentId, TENANT_ID))
                .thenReturn(Optional.of(attachment));

        assertThrows(IllegalArgumentException.class,
                () -> attachmentService.validateCommentAttachmentReferences(
                        List.of(url), CommentTargetType.TASK, UUID.randomUUID().toString()));
        assertFalse(attachmentService.findProtectedReference(
                url, CommentTargetType.INVOICE, "42").isPresent());
    }

    @Test
    void projectAggregateDoesNotExposeMetadataWhenTargetAuthorizationFails() {
        UUID attachmentId = UUID.randomUUID();
        Attachment attachment = attachment(attachmentId, UUID.randomUUID().toString());
        String url = "/api/attachments/" + attachmentId;
        when(attachmentRepository.findByIdAndTenantId(attachmentId, TENANT_ID))
                .thenReturn(Optional.of(attachment));
        when(targetAccessService.authorize(
                CommentTargetType.PROJECT, PROJECT_ID.toString(), ACTOR_ID))
                .thenThrow(new AccessDeniedException("denied"));

        assertTrue(attachmentService.findAuthorizedProtectedReference(
                url, CommentTargetType.PROJECT, PROJECT_ID.toString(), ACTOR_ID).isEmpty());
    }

    private void authorizeProject() {
        when(targetAccessService.authorize(
                CommentTargetType.PROJECT, PROJECT_ID.toString(), ACTOR_ID))
                .thenReturn(new TargetAccessService.AuthorizedTarget(
                        CommentTargetType.PROJECT,
                        PROJECT_ID.toString(),
                        actor,
                        null));
    }

    private Attachment attachment(UUID id, String storageKey) {
        Attachment attachment = new Attachment();
        attachment.setId(id);
        attachment.setTenantId(TENANT_ID);
        attachment.setUploader(actor);
        attachment.setTargetType(CommentTargetType.PROJECT);
        attachment.setTargetId(PROJECT_ID.toString());
        attachment.setStorageKey(storageKey);
        attachment.setOriginalFilename("scope.pdf");
        attachment.setMediaType("application/pdf");
        attachment.setSizeBytes(9);
        attachment.setCreatedAt(Instant.now());
        return attachment;
    }
}
