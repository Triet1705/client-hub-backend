package com.clienthub.web.controller;

import com.clienthub.application.dto.user.ChangePasswordRequest;
import com.clienthub.application.dto.user.CurrentUserResponse;
import com.clienthub.application.dto.user.UpdateUserPreferencesRequest;
import com.clienthub.application.dto.user.UpdateUserProfileRequest;
import com.clienthub.application.dto.WalletBindingRequest;
import com.clienthub.application.service.UserService;
import com.clienthub.infrastructure.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "Current user profile, preferences, and wallet endpoints")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    @Operation(summary = "Get current user profile and preferences")
    public ResponseEntity<CurrentUserResponse> getCurrentUser(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(userService.getCurrentUser(currentUser.getId()));
    }

    @PatchMapping("/me/profile")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    @Operation(summary = "Update current user profile")
    public ResponseEntity<CurrentUserResponse> updateProfile(
            @Valid @RequestBody UpdateUserProfileRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(userService.updateProfile(currentUser.getId(), request));
    }

    @PutMapping("/me/preferences")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    @Operation(summary = "Update current user preferences")
    public ResponseEntity<CurrentUserResponse> updatePreferences(
            @Valid @RequestBody UpdateUserPreferencesRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(userService.updatePreferences(currentUser.getId(), request));
    }

    @PatchMapping("/me/password")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    @Operation(summary = "Change current user password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        userService.changePassword(currentUser.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/wallet")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    @Operation(summary = "Bind wallet address to current user")
    public ResponseEntity<Void> bindWallet(
            @Valid @RequestBody WalletBindingRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        
        userService.updateWalletAddress(currentUser.getId(), request.getWalletAddress());
        return ResponseEntity.noContent().build();
    }
}
