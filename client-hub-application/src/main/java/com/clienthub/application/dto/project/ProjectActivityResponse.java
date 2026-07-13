package com.clienthub.application.dto.project;

import java.time.Instant;

public class ProjectActivityResponse {
    private Long id;
    private String action;
    private String label;
    private String entityType;
    private String entityId;
    private String actorName;
    private Instant createdAt;

    public ProjectActivityResponse() {
    }

    public ProjectActivityResponse(Long id, String action, String label, String entityType,
                                   String entityId, String actorName, Instant createdAt) {
        this.id = id;
        this.action = action;
        this.label = label;
        this.entityType = entityType;
        this.entityId = entityId;
        this.actorName = actorName;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
