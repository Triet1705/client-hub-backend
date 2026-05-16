package com.clienthub.application.dto.admin;

import com.clienthub.domain.entity.Project;
import com.clienthub.domain.enums.ProjectStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminProjectResponse(
        UUID id,
        String title,
        ProjectStatus status,
        String tenantId,
        String ownerEmail,
        String ownerName,
        long memberCount,
        long taskCount,
        BigDecimal budget,
        LocalDate deadline,
        Instant createdAt
) {
    public static AdminProjectResponse from(Project project, long memberCount, long taskCount) {
        return new AdminProjectResponse(
                project.getId(),
                project.getTitle(),
                project.getStatus(),
                project.getTenantId(),
                project.getOwner() != null ? project.getOwner().getEmail() : null,
                project.getOwner() != null ? project.getOwner().getFullName() : null,
                memberCount,
                taskCount,
                project.getBudget(),
                project.getDeadline(),
                project.getCreatedAt()
        );
    }
}
