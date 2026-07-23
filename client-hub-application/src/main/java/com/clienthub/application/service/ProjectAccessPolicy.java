package com.clienthub.application.service;

import com.clienthub.domain.entity.Project;
import com.clienthub.domain.enums.Role;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

final class ProjectAccessPolicy {

    private ProjectAccessPolicy() {
    }

    static void requireReadAccess(Project project,
                                  UUID currentUserId,
                                  Role callerRole,
                                  boolean explicitFreelancerMember,
                                  String denialMessage) {
        boolean isAdmin = callerRole == Role.ADMIN;
        boolean isOwningClient = callerRole == Role.CLIENT
                && project.getOwner() != null
                && project.getOwner().getId().equals(currentUserId);
        boolean isMemberFreelancer = callerRole == Role.FREELANCER
                && explicitFreelancerMember;

        if (!isAdmin && !isOwningClient && !isMemberFreelancer) {
            throw new AccessDeniedException(denialMessage);
        }
    }
}
