package com.clienthub.application.dto.task;

import com.clienthub.domain.enums.TaskPriority;
import com.clienthub.domain.enums.TaskStatus;
import com.clienthub.application.dto.UserSummaryDto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public class TaskResponse {
    private UUID id;
    private String title;
    private String description;

    private UUID projectId;
    private String projectTitle;

    private UserSummaryDto assignedTo;

    private TaskStatus status;
    private TaskPriority priority;
    private Integer estimatedHours;
    private Integer actualHours;
    private LocalDateTime dueDate;

    private Instant createdAt;
    private Instant updatedAt;

    public TaskResponse() {}

    public TaskResponse(UUID id, String title, String description, UUID projectId, String projectTitle,
                        UserSummaryDto assignedTo, TaskStatus status, TaskPriority priority, Integer estimatedHours,
                        Integer actualHours, LocalDateTime dueDate, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.projectId = projectId;
        this.projectTitle = projectTitle;
        this.assignedTo = assignedTo;
        this.status = status;
        this.priority = priority;
        this.estimatedHours = estimatedHours;
        this.actualHours = actualHours;
        this.dueDate = dueDate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public String getProjectTitle() { return projectTitle; }
    public void setProjectTitle(String projectTitle) { this.projectTitle = projectTitle; }

    public UserSummaryDto getAssignedTo() { return assignedTo; }
    public void setAssignedTo(UserSummaryDto assignedTo) { this.assignedTo = assignedTo; }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }

    public Integer getEstimatedHours() { return estimatedHours; }
    public void setEstimatedHours(Integer estimatedHours) { this.estimatedHours = estimatedHours; }

    public Integer getActualHours() { return actualHours; }
    public void setActualHours(Integer actualHours) { this.actualHours = actualHours; }

    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static TaskResponseBuilder builder() {
        return new TaskResponseBuilder();
    }

    public static class TaskResponseBuilder {
        private UUID id;
        private String title;
        private String description;
        private UUID projectId;
        private String projectTitle;
        private UserSummaryDto assignedTo;
        private TaskStatus status;
        private TaskPriority priority;
        private Integer estimatedHours;
        private Integer actualHours;
        private LocalDateTime dueDate;
        private Instant createdAt;
        private Instant updatedAt;

        TaskResponseBuilder() {}

        public TaskResponseBuilder id(UUID id) { this.id = id; return this; }
        public TaskResponseBuilder title(String title) { this.title = title; return this; }
        public TaskResponseBuilder description(String description) { this.description = description; return this; }
        public TaskResponseBuilder projectId(UUID projectId) { this.projectId = projectId; return this; }
        public TaskResponseBuilder projectTitle(String projectTitle) { this.projectTitle = projectTitle; return this; }
        public TaskResponseBuilder assignedTo(UserSummaryDto assignedTo) { this.assignedTo = assignedTo; return this; }
        public TaskResponseBuilder status(TaskStatus status) { this.status = status; return this; }
        public TaskResponseBuilder priority(TaskPriority priority) { this.priority = priority; return this; }
        public TaskResponseBuilder estimatedHours(Integer estimatedHours) { this.estimatedHours = estimatedHours; return this; }
        public TaskResponseBuilder actualHours(Integer actualHours) { this.actualHours = actualHours; return this; }
        public TaskResponseBuilder dueDate(LocalDateTime dueDate) { this.dueDate = dueDate; return this; }
        public TaskResponseBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public TaskResponseBuilder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public TaskResponse build() {
            return new TaskResponse(id, title, description, projectId, projectTitle, assignedTo, status, priority, estimatedHours, actualHours, dueDate, createdAt, updatedAt);
        }
    }
}
