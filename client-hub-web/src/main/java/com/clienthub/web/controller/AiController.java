package com.clienthub.web.controller;

import com.clienthub.infrastructure.ai.TaskDraftDto;
import com.clienthub.infrastructure.ai.AiTaskService;
import com.clienthub.infrastructure.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI Services", description = "Endpoints for AI-powered features")
public class AiController {

    private final AiTaskService aiTaskService;

    public AiController(AiTaskService aiTaskService) {
        this.aiTaskService = aiTaskService;
    }

    @Operation(summary = "Extract Task from PDF", description = "Uploads a PDF requirement file, extracts text, and uses AI to generate a task draft.")
    @ApiResponse(responseCode = "200", description = "Task draft generated successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TaskDraftDto.class)))
    @ApiResponse(responseCode = "400", description = "Invalid file format")
    @ApiResponse(responseCode = "422", description = "Could not process PDF (Encrypted/Scanned)")
    @PostMapping(value = "/extract-task", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    public ResponseEntity<TaskDraftDto> extractTaskFromPdf(
            @Parameter(description = "PDF file to analyze", required = true)
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        TaskDraftDto taskDraft = aiTaskService.extractTaskFromPdf(file);
        return ResponseEntity.ok(taskDraft);
    }
}
