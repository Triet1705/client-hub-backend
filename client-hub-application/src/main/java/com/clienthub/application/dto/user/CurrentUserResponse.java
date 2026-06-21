package com.clienthub.application.dto.user;

import com.clienthub.domain.entity.User;
import com.clienthub.domain.entity.UserPreferences;
import com.clienthub.domain.entity.UserProfile;

import java.time.Instant;
import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        String email,
        String fullName,
        String role,
        String tenantId,
        boolean active,
        String walletAddress,
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt,
        UserProfileResponse profile,
        UserPreferencesResponse preferences
) {
    public static CurrentUserResponse from(User user, UserProfile profile, UserPreferences preferences) {
        return new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getTenantId(),
                user.isActive(),
                user.getWalletAddress(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getLastLoginAt(),
                UserProfileResponse.from(profile),
                UserPreferencesResponse.from(preferences)
        );
    }
}
