package com.clienthub.web.controller;

import com.clienthub.domain.entity.Comment;
import com.clienthub.domain.enums.CommentTargetType;
import com.clienthub.application.dto.communication.CommentRequest;
import com.clienthub.application.dto.communication.CommentResponse;
import com.clienthub.application.mapper.CommentMapper;
import com.clienthub.infrastructure.security.CustomUserDetails;
import com.clienthub.application.service.CommunicationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/comments")
public class CommunicationController {

    private final CommunicationService communicationService;
    private final CommentMapper commentMapper;

    public CommunicationController(CommunicationService communicationService, CommentMapper commentMapper) {
        this.communicationService = communicationService;
        this.commentMapper = commentMapper;
    }

    /**
     * Create a new comment.
     * Example Request Body:
     * {
     *   "targetType": "TASK",
     *   "targetId": "123e4567-e89b-12d3-a456-426614174000",
     *   "content": "This task needs clarification on requirements"
     * }
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    public ResponseEntity<CommentResponse> postComment(
            @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        Comment comment = communicationService.postComment(
                request.getTargetType(),
                request.getTargetId(),
                request.getContent(),
                currentUser.getId()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentMapper.toResponse(comment));
    }

    /**
     * Get comments for a target.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    public ResponseEntity<Page<CommentResponse>> getComments(
            @RequestParam CommentTargetType targetType,
            @RequestParam String targetId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        Page<Comment> comments = communicationService.getComments(
                targetType,
                targetId,
                pageable,
                currentUser.getId()
        );

        return ResponseEntity.ok(comments.map(commentMapper::toResponse));
    }

    /**
     * Update comment. Path must include ID to avoid conflict with create.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    public ResponseEntity<CommentResponse> updateComment(
            @PathVariable Long id,
            @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        Comment updatedComment = communicationService.updateComment(
                id,
                request.getContent(),
                currentUser.getId()
        );

        return ResponseEntity.ok(commentMapper.toResponse(updatedComment));
    }

    /**
     * Delete comment. Path must include ID.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        communicationService.deleteComment(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}