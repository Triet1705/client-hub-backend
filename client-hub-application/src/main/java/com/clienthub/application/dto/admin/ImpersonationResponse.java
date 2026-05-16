package com.clienthub.application.dto.admin;

import com.clienthub.domain.enums.Role;

import java.util.UUID;

public record ImpersonationResponse(
        String accessToken,
        UUID id,
        String email,
        Role role,
        String tenantId,
        boolean impersonated
) {
}
