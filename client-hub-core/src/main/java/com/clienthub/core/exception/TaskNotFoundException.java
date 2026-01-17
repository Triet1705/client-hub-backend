package com.clienthub.core.exception;

import java.util.UUID;

/**
 * Exception thrown when a requested Task is not found.
 */
public class TaskNotFoundException extends RuntimeException {
    
    private final UUID taskId;

    public TaskNotFoundException(UUID taskId) {
        super(String.format("Task not found with ID: %s", taskId));
        this.taskId = taskId;
    }

    public TaskNotFoundException(UUID taskId, String tenantId) {
        super(String.format("Task not found with ID: %s for tenant: %s", taskId, tenantId));
        this.taskId = taskId;
    }

    public UUID getTaskId() {
        return taskId;
    }
}
