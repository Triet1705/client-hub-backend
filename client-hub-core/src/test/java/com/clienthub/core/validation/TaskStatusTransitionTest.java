package com.clienthub.core.validation;

import com.clienthub.core.domain.enums.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TaskStatusTransition state machine validation.
 * Ensures business rules for task lifecycle are correctly enforced.
 */
class TaskStatusTransitionTest {

    @Test
    @DisplayName("Should allow valid transitions from TODO")
    void testValidTransitionsFromTodo() {
        assertTrue(TaskStatusTransition.isTransitionAllowed(TaskStatus.TODO, TaskStatus.IN_PROGRESS));
        assertTrue(TaskStatusTransition.isTransitionAllowed(TaskStatus.TODO, TaskStatus.CANCELED));
    }

    @Test
    @DisplayName("Should reject invalid transitions from TODO")
    void testInvalidTransitionsFromTodo() {
        assertFalse(TaskStatusTransition.isTransitionAllowed(TaskStatus.TODO, TaskStatus.DONE));
    }

    @Test
    @DisplayName("Should allow valid transitions from IN_PROGRESS")
    void testValidTransitionsFromInProgress() {
        assertTrue(TaskStatusTransition.isTransitionAllowed(TaskStatus.IN_PROGRESS, TaskStatus.TODO));
        assertTrue(TaskStatusTransition.isTransitionAllowed(TaskStatus.IN_PROGRESS, TaskStatus.DONE));
        assertTrue(TaskStatusTransition.isTransitionAllowed(TaskStatus.IN_PROGRESS, TaskStatus.CANCELED));
    }

    @Test
    @DisplayName("Should allow reopening from DONE")
    void testValidTransitionsFromDone() {
        assertTrue(TaskStatusTransition.isTransitionAllowed(TaskStatus.DONE, TaskStatus.IN_PROGRESS));
    }

    @Test
    @DisplayName("Should reject invalid transitions from DONE")
    void testInvalidTransitionsFromDone() {
        assertFalse(TaskStatusTransition.isTransitionAllowed(TaskStatus.DONE, TaskStatus.TODO));
        assertFalse(TaskStatusTransition.isTransitionAllowed(TaskStatus.DONE, TaskStatus.CANCELED));
    }

    @Test
    @DisplayName("Should allow reactivation from CANCELED")
    void testValidTransitionsFromCanceled() {
        assertTrue(TaskStatusTransition.isTransitionAllowed(TaskStatus.CANCELED, TaskStatus.TODO));
    }

    @Test
    @DisplayName("Should reject invalid transitions from CANCELED")
    void testInvalidTransitionsFromCanceled() {
        assertFalse(TaskStatusTransition.isTransitionAllowed(TaskStatus.CANCELED, TaskStatus.IN_PROGRESS));
        assertFalse(TaskStatusTransition.isTransitionAllowed(TaskStatus.CANCELED, TaskStatus.DONE));
    }

    @Test
    @DisplayName("Should allow same status transition (no-op)")
    void testSameStatusTransition() {
        assertTrue(TaskStatusTransition.isTransitionAllowed(TaskStatus.TODO, TaskStatus.TODO));
        assertTrue(TaskStatusTransition.isTransitionAllowed(TaskStatus.IN_PROGRESS, TaskStatus.IN_PROGRESS));
        assertTrue(TaskStatusTransition.isTransitionAllowed(TaskStatus.DONE, TaskStatus.DONE));
        assertTrue(TaskStatusTransition.isTransitionAllowed(TaskStatus.CANCELED, TaskStatus.CANCELED));
    }

    @Test
    @DisplayName("Should handle null status safely")
    void testNullStatusHandling() {
        assertFalse(TaskStatusTransition.isTransitionAllowed(null, TaskStatus.TODO));
        assertFalse(TaskStatusTransition.isTransitionAllowed(TaskStatus.TODO, null));
        assertFalse(TaskStatusTransition.isTransitionAllowed(null, null));
    }

    @Test
    @DisplayName("Should return correct allowed transitions for TODO")
    void testGetAllowedTransitionsFromTodo() {
        Set<TaskStatus> allowed = TaskStatusTransition.getAllowedTransitions(TaskStatus.TODO);
        
        assertEquals(2, allowed.size());
        assertTrue(allowed.contains(TaskStatus.IN_PROGRESS));
        assertTrue(allowed.contains(TaskStatus.CANCELED));
        assertFalse(allowed.contains(TaskStatus.DONE));
    }

    @Test
    @DisplayName("Should return correct allowed transitions for IN_PROGRESS")
    void testGetAllowedTransitionsFromInProgress() {
        Set<TaskStatus> allowed = TaskStatusTransition.getAllowedTransitions(TaskStatus.IN_PROGRESS);
        
        assertEquals(3, allowed.size());
        assertTrue(allowed.contains(TaskStatus.TODO));
        assertTrue(allowed.contains(TaskStatus.DONE));
        assertTrue(allowed.contains(TaskStatus.CANCELED));
    }

    @Test
    @DisplayName("Should return correct allowed transitions for DONE")
    void testGetAllowedTransitionsFromDone() {
        Set<TaskStatus> allowed = TaskStatusTransition.getAllowedTransitions(TaskStatus.DONE);
        
        assertEquals(1, allowed.size());
        assertTrue(allowed.contains(TaskStatus.IN_PROGRESS));
    }

    @Test
    @DisplayName("Should return correct allowed transitions for CANCELED")
    void testGetAllowedTransitionsFromCanceled() {
        Set<TaskStatus> allowed = TaskStatusTransition.getAllowedTransitions(TaskStatus.CANCELED);
        
        assertEquals(1, allowed.size());
        assertTrue(allowed.contains(TaskStatus.TODO));
    }

    @ParameterizedTest
    @MethodSource("provideValidTransitions")
    @DisplayName("Should validate all documented valid transitions")
    void testAllValidTransitions(TaskStatus from, TaskStatus to) {
        assertTrue(TaskStatusTransition.isTransitionAllowed(from, to),
                String.format("Transition %s -> %s should be allowed", from, to));
    }

    @ParameterizedTest
    @MethodSource("provideInvalidTransitions")
    @DisplayName("Should reject all documented invalid transitions")
    void testAllInvalidTransitions(TaskStatus from, TaskStatus to) {
        assertFalse(TaskStatusTransition.isTransitionAllowed(from, to),
                String.format("Transition %s -> %s should be rejected", from, to));
    }

    private static Stream<Arguments> provideValidTransitions() {
        return Stream.of(
                // From TODO
                Arguments.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS),
                Arguments.of(TaskStatus.TODO, TaskStatus.CANCELED),
                
                // From IN_PROGRESS
                Arguments.of(TaskStatus.IN_PROGRESS, TaskStatus.TODO),
                Arguments.of(TaskStatus.IN_PROGRESS, TaskStatus.DONE),
                Arguments.of(TaskStatus.IN_PROGRESS, TaskStatus.CANCELED),
                
                // From DONE
                Arguments.of(TaskStatus.DONE, TaskStatus.IN_PROGRESS),
                
                // From CANCELED
                Arguments.of(TaskStatus.CANCELED, TaskStatus.TODO),
                
                // Same status (no-op)
                Arguments.of(TaskStatus.TODO, TaskStatus.TODO),
                Arguments.of(TaskStatus.IN_PROGRESS, TaskStatus.IN_PROGRESS),
                Arguments.of(TaskStatus.DONE, TaskStatus.DONE),
                Arguments.of(TaskStatus.CANCELED, TaskStatus.CANCELED)
        );
    }

    private static Stream<Arguments> provideInvalidTransitions() {
        return Stream.of(
                // From TODO
                Arguments.of(TaskStatus.TODO, TaskStatus.DONE),
                
                // From DONE
                Arguments.of(TaskStatus.DONE, TaskStatus.TODO),
                Arguments.of(TaskStatus.DONE, TaskStatus.CANCELED),
                
                // From CANCELED
                Arguments.of(TaskStatus.CANCELED, TaskStatus.IN_PROGRESS),
                Arguments.of(TaskStatus.CANCELED, TaskStatus.DONE)
        );
    }
}
