package com.clienthub.application.dto.admin;

import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.Role;

import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        String fullName,
        Role role,
        String tenantId,
        boolean active,
        String walletAddress,
        Instant createdAt,
        Instant lastLoginAt
) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getTenantId(),
                user.isActive(),
                user.getWalletAddress(),
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
    }
}
