package com.clienthub.web.controller;

import com.clienthub.application.dto.notification.MarkAllReadResponse;
import com.clienthub.application.dto.notification.NotificationResponse;
import com.clienthub.application.dto.notification.UnreadCountResponse;
import com.clienthub.application.service.NotificationService;
import com.clienthub.infrastructure.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Recipient-scoped notification APIs")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
        @Operation(summary = "Get notifications", description = "Returns paginated notifications of current authenticated user.")
        @ApiResponse(responseCode = "200", description = "Notifications fetched successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NotificationResponse.class)))
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Parameter(description = "If true, returns only unread notifications")
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(notificationService.getNotifications(currentUser.getId(), pageable, unreadOnly));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
        @Operation(summary = "Get unread count", description = "Returns unread notification count of current authenticated user.")
        @ApiResponse(responseCode = "200", description = "Unread count fetched successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UnreadCountResponse.class)))
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ResponseEntity.ok(notificationService.getUnreadCount(currentUser.getId()));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
        @Operation(summary = "Mark notification as read", description = "Marks one notification as read for current authenticated user.")
        @ApiResponse(responseCode = "200", description = "Notification marked as read",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NotificationResponse.class)))
        @ApiResponse(responseCode = "404", description = "Notification not found for current user")
    public ResponseEntity<NotificationResponse> markAsRead(
            @Parameter(description = "Notification ID", required = true)
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ResponseEntity.ok(notificationService.markAsRead(id, currentUser.getId()));
    }

    @PatchMapping("/read-all")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
        @Operation(summary = "Mark all notifications as read", description = "Marks all unread notifications as read for current authenticated user.")
        @ApiResponse(responseCode = "200", description = "All unread notifications marked as read",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MarkAllReadResponse.class)))
    public ResponseEntity<MarkAllReadResponse> markAllAsRead(
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ResponseEntity.ok(notificationService.markAllAsRead(currentUser.getId()));
    }
}
