package com.clienthub.application.service;

import com.clienthub.common.context.TenantContext;
import com.clienthub.application.dto.UserSearchRequest;
import com.clienthub.application.dto.UserSummaryDto;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.repository.UserRepository;
import com.clienthub.application.specification.UserSpecification;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<UserSummaryDto> findAllUsersSystemWide(UserSearchRequest request) {
        Session session = entityManager.unwrap(Session.class);
        Filter tenantFilter = session.getEnabledFilter("tenantFilter");
        
        boolean wasFilterEnabled = (tenantFilter != null);
        if (wasFilterEnabled) {
            session.disableFilter("tenantFilter");
            log.debug("Tenant filter disabled for system-wide user query");
        }

        try {
            Pageable pageable = PageRequest.of(
                request.getPage(),
                Math.min(request.getPageSize(), 100),
                Sort.by(
                    Sort.Direction.fromString(request.getSortDir()),
                    request.getSortBy()
                )
            );

            Specification<User> spec = UserSpecification.searchUsers(request);
            
            Page<User> users = userRepository.findAll(spec, pageable);

            return users.map(this::toSummaryDTO);

        } finally {
            if (wasFilterEnabled) {
                String currentTenant = TenantContext.getTenantId();
                if (currentTenant != null) {
                    session.enableFilter("tenantFilter")
                           .setParameter("tenantId", currentTenant);
                    log.debug("Tenant filter re-enabled with tenantId: {}", currentTenant);
                } else {
                    session.enableFilter("tenantFilter");
                    log.warn("Tenant filter re-enabled without tenantId parameter - context missing!");
                }
            }
        }
    }

    /**
     * Find user by ID (system-wide, ignores tenant filter)
     * Required for Admin Impersonation feature to access users across all tenants
     * 
     * @param userId User's unique identifier
     * @return Optional containing User if found
     */
    @Transactional(readOnly = true)
    public Optional<User> findById(UUID userId) {
        Session session = entityManager.unwrap(Session.class);
        Filter tenantFilter = session.getEnabledFilter("tenantFilter");
        
        boolean wasFilterEnabled = (tenantFilter != null);
        if (wasFilterEnabled) {
            session.disableFilter("tenantFilter");
            log.debug("Tenant filter disabled for admin findById query - userId: {}", userId);
        }

        try {
            return userRepository.findById(userId);
        } finally {
            if (wasFilterEnabled) {
                String currentTenant = TenantContext.getTenantId();
                if (currentTenant != null) {
                    session.enableFilter("tenantFilter")
                           .setParameter("tenantId", currentTenant);
                    log.debug("Tenant filter re-enabled with tenantId: {}", currentTenant);
                } else {
                    session.enableFilter("tenantFilter");
                    log.warn("Tenant filter re-enabled without tenantId parameter - context missing!");
                }
            }
        }
    }

    /**
     * TODO : Add last_login_at field to users table and update in AuthService.login()
     * TODO : Implement activeSessionCounts by injecting RefreshTokenRepository
     */
    private UserSummaryDto toSummaryDTO(User user) {
        // TODO: Replace with real lastLoginAt field when available
        java.time.Instant lastLogin = user.getUpdatedAt() != null 
            ? user.getUpdatedAt()
            : user.getCreatedAt();
            
        return new UserSummaryDto(
            user.getId(),
            user.getEmail(),
            user.getRole().name(),
            user.getTenantId(),
            user.isActive() ? "ACTIVE" : "INACTIVE",
            0, // TODO: Replace with real count from RefreshTokenRepository.countByUserAndRevokedFalse(user)
            lastLogin
        );
    }
}
