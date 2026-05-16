package com.clienthub.web.dto.admin;

import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(
        @NotNull(message = "Active status is required")
        Boolean active
) {}
