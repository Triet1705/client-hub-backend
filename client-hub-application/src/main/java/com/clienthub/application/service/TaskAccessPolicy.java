package com.clienthub.application.service;

import com.clienthub.domain.entity.Project;
import com.clienthub.domain.entity.Task;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.Role;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

final class TaskAccessPolicy {

    private TaskAccessPolicy() {
    }

    static void requireProjectCreateAccess(Project project, User actor, boolean explicitFreelancerMember) {
        boolean isAdmin = actor.getRole() == Role.ADMIN;
        boolean isOwningClient = actor.getRole() == Role.CLIENT
                && project.getOwner() != null
                && actor.getId().equals(project.getOwner().getId());
        boolean isMemberFreelancer = actor.getRole() == Role.FREELANCER
                && explicitFreelancerMember;

        if (!isAdmin && !isOwningClient && !isMemberFreelancer) {
            throw new AccessDeniedException("You are not allowed to create tasks in this project");
        }
    }

    static void requireReadOrUpdateAccess(Task task, User actor) {
        if (!isAdmin(actor) && !isOwningClient(task, actor) && !isAssignedFreelancer(task, actor)) {
            throw new AccessDeniedException(
                    "Only the project owner, task assignee, or Administrator can access this task");
        }
    }

    static void requireOwnerOrAdmin(Task task, User actor, String message) {
        if (!isAdmin(actor) && !isOwningClient(task, actor)) {
            throw new AccessDeniedException(message);
        }
    }

    static void requireUnassignAccess(Task task, User actor) {
        if (!isAdmin(actor) && !isOwningClient(task, actor) && !isAssignedFreelancer(task, actor)) {
            throw new AccessDeniedException(
                    "Only the project owner, assigned Freelancer, or Administrator can unassign this task");
        }
    }

    private static boolean isAdmin(User actor) {
        return actor.getRole() == Role.ADMIN;
    }

    private static boolean isOwningClient(Task task, User actor) {
        UUID ownerId = task.getProject() != null && task.getProject().getOwner() != null
                ? task.getProject().getOwner().getId()
                : null;
        return actor.getRole() == Role.CLIENT && actor.getId().equals(ownerId);
    }

    private static boolean isAssignedFreelancer(Task task, User actor) {
        UUID assigneeId = task.getAssignedTo() != null ? task.getAssignedTo().getId() : null;
        return actor.getRole() == Role.FREELANCER && actor.getId().equals(assigneeId);
    }
}
