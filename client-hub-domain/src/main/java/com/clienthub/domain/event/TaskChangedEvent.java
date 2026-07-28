package com.clienthub.domain.event;

import com.clienthub.domain.enums.TaskStatus;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable task change snapshot suitable for after-commit delivery.
 */
public class TaskChangedEvent extends ApplicationEvent {

    private final UUID taskId;
    private final UUID projectId;
    private final UUID projectOwnerId;
    private final UUID assigneeId;
    private final UUID previousAssigneeId;
    private final TaskStatus status;
    private final String changeType;
    private final Instant changedAt;

    public TaskChangedEvent(
            Object source,
            UUID taskId,
            UUID projectId,
            UUID projectOwnerId,
            UUID assigneeId,
            UUID previousAssigneeId,
            TaskStatus status,
            String changeType) {
        super(source);
        this.taskId = taskId;
        this.projectId = projectId;
        this.projectOwnerId = projectOwnerId;
        this.assigneeId = assigneeId;
        this.previousAssigneeId = previousAssigneeId;
        this.status = status;
        this.changeType = changeType;
        this.changedAt = Instant.now();
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getProjectOwnerId() {
        return projectOwnerId;
    }

    public UUID getAssigneeId() {
        return assigneeId;
    }

    public UUID getPreviousAssigneeId() {
        return previousAssigneeId;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public String getChangeType() {
        return changeType;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
