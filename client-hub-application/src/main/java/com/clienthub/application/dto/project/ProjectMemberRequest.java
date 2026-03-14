package com.clienthub.application.dto.project;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class ProjectMemberRequest {

    @NotNull(message = "userId is required")
    private UUID userId;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }
}
