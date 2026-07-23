package com.clienthub.web.controller;

import com.clienthub.application.dto.AttachmentDownload;
import com.clienthub.application.dto.AttachmentResponseDto;
import com.clienthub.application.service.AttachmentService;
import com.clienthub.domain.enums.CommentTargetType;
import com.clienthub.infrastructure.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/attachments")
@Tag(name = "Attachments", description = "Target-bound protected attachment endpoints")
@SecurityRequirement(name = "bearerAuth")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload a protected attachment",
            description = "Authorises and binds the file to a PROJECT, TASK, or INVOICE target before storage.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Attachment stored and bound to the target"),
            @ApiResponse(responseCode = "400", description = "Invalid target or file"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Caller is unrelated to the target"),
            @ApiResponse(responseCode = "404", description = "Target does not exist in the caller's tenant")
    })
    public ResponseEntity<AttachmentResponseDto> uploadAttachment(
            @RequestParam CommentTargetType targetType,
            @RequestParam String targetId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        AttachmentResponseDto response =
                attachmentService.uploadDocument(targetType, targetId, file, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{attachmentId}")
    @Operation(
            summary = "Download a protected attachment",
            description = "Resolves tenant and target relationship authorisation before accessing storage.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Attachment streamed"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Caller is unrelated to the attachment target"),
            @ApiResponse(responseCode = "404", description = "Attachment is missing or belongs to another tenant")
    })
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable UUID attachmentId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        AttachmentDownload download = attachmentService.getDownload(attachmentId, currentUser.getId());
        try {
            Resource resource = new UrlResource(download.path().toUri());
            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(download.originalFilename(), StandardCharsets.UTF_8)
                    .build();

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(download.mediaType()))
                    .contentLength(download.sizeBytes())
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .header("X-Content-Type-Options", "nosniff")
                    .body(resource);
        } catch (MalformedURLException exception) {
            throw new IllegalStateException("Unable to resolve attachment resource", exception);
        }
    }
}
