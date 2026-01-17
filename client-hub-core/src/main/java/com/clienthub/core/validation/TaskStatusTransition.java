package com.clienthub.core.validation;

import com.clienthub.core.domain.enums.TaskStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Validates task status transitions according to business rules.
 * Implements state machine pattern for task lifecycle management.
 */
public class TaskStatusTransition {

    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(TaskStatus.class);

    static {
        // Define allowed state transitions
        ALLOWED_TRANSITIONS.put(TaskStatus.TODO, EnumSet.of(
                TaskStatus.IN_PROGRESS,
                TaskStatus.CANCELED
        ));

        ALLOWED_TRANSITIONS.put(TaskStatus.IN_PROGRESS, EnumSet.of(
                TaskStatus.TODO,      // Can move back to TODO if needed
                TaskStatus.DONE,
                TaskStatus.CANCELED
        ));

        ALLOWED_TRANSITIONS.put(TaskStatus.DONE, EnumSet.of(
                TaskStatus.IN_PROGRESS  // Can reopen if needed
        ));

        ALLOWED_TRANSITIONS.put(TaskStatus.CANCELED, EnumSet.of(
                TaskStatus.TODO         // Can reactivate canceled tasks
        ));
    }

    /**
     * Checks if transition from current status to new status is allowed.
     *
     * @param currentStatus the current task status
     * @param newStatus     the requested new status
     * @return true if transition is allowed, false otherwise
     */
    public static boolean isTransitionAllowed(TaskStatus currentStatus, TaskStatus newStatus) {
        if (currentStatus == null || newStatus == null) {
            return false;
        }

        // Same status is always allowed (no-op)
        if (currentStatus == newStatus) {
            return true;
        }

        Set<TaskStatus> allowedTargets = ALLOWED_TRANSITIONS.get(currentStatus);
        return allowedTargets != null && allowedTargets.contains(newStatus);
    }

    /**
     * Gets all allowed target statuses from a given current status.
     *
     * @param currentStatus the current task status
     * @return set of allowed target statuses
     */
    public static Set<TaskStatus> getAllowedTransitions(TaskStatus currentStatus) {
        return ALLOWED_TRANSITIONS.getOrDefault(currentStatus, EnumSet.noneOf(TaskStatus.class));
    }
}
