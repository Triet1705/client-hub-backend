package com.clienthub.application.dto;

import java.time.Instant;
import java.util.UUID;

public class UserSummaryDto {
    private UUID id;
    private String email;
    private String role;
    private String tenantId;
    private String status;
    private int activeSessionCounts;
    private Instant lastLoginAt;

    public UserSummaryDto(UUID id, String email, String role, String tenantId, String status,
                          int activeSessionCounts, Instant lastLoginAt) {
        this.id = id;
        this.email = email;
        this.role = role;
        this.tenantId = tenantId;
        this.status = status;
        this.activeSessionCounts = activeSessionCounts;
        this.lastLoginAt = lastLoginAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId (UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getActiveSessionCounts() {
        return activeSessionCounts;
    }

    public void setActiveSessionCounts(int activeSessionCounts) {
        this.activeSessionCounts = activeSessionCounts;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
