package com.clienthub.application.exception;

import com.clienthub.domain.enums.TaskStatus;

import java.util.UUID;

/**
 * Exception thrown when attempting an invalid task state transition.
 */
public class InvalidTaskStateException extends RuntimeException {
    
    private final UUID taskId;
    private final TaskStatus currentStatus;
    private final TaskStatus requestedStatus;

    public InvalidTaskStateException(UUID taskId, TaskStatus currentStatus, TaskStatus requestedStatus) {
        super(String.format("Invalid task state transition for task %s: cannot move from %s to %s", 
                taskId, currentStatus, requestedStatus));
        this.taskId = taskId;
        this.currentStatus = currentStatus;
        this.requestedStatus = requestedStatus;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public TaskStatus getCurrentStatus() {
        return currentStatus;
    }

    public TaskStatus getRequestedStatus() {
        return requestedStatus;
    }
}
