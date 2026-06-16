package com.clienthub.web.controller;

import com.clienthub.application.dto.AttachmentResponseDto;
import com.clienthub.application.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/attachments")
@Tag(name = "Attachments", description = "Endpoints for managing file attachments")
@SecurityRequirement(name = "bearerAuth")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload an attachment", description = "Uploads a file to be used as an attachment in comments or tasks.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "File uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file type or size"),
            @ApiResponse(responseCode = "403", description = "Unauthorized access")
    })
    public ResponseEntity<AttachmentResponseDto> uploadAttachment(@RequestParam("file") MultipartFile file) {
        AttachmentResponseDto response = attachmentService.uploadDocument(file);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{filename}")
    @Operation(summary = "Download an attachment", description = "Downloads a previously uploaded file. Requires authentication.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "File streamed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid filename"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "404", description = "File not found")
    })
    public ResponseEntity<Resource> downloadAttachment(@PathVariable String filename) {
        Path filePath = attachmentService.resolveDownloadPath(filename);

        try {
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}