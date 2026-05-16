package com.clienthub.web.dto.admin;

import com.clienthub.domain.enums.Role;
import jakarta.validation.constraints.NotNull;

public record UserRoleRequest(
        @NotNull(message = "Role is required")
        Role role
) {}
