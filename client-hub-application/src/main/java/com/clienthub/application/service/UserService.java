package com.clienthub.application.service;

import com.clienthub.common.context.TenantContext;
import com.clienthub.application.dto.UserSearchRequest;
import com.clienthub.application.dto.UserSummaryDto;
import com.clienthub.application.dto.user.ChangePasswordRequest;
import com.clienthub.application.dto.user.CurrentUserResponse;
import com.clienthub.application.dto.user.UpdateUserPreferencesRequest;
import com.clienthub.application.dto.user.UpdateUserProfileRequest;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.entity.UserPreferences;
import com.clienthub.domain.entity.UserProfile;
import com.clienthub.domain.enums.NotificationType;
import com.clienthub.domain.repository.UserPreferencesRepository;
import com.clienthub.domain.repository.UserProfileRepository;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    public UserService(UserRepository userRepository,
                       UserProfileRepository userProfileRepository,
                       UserPreferencesRepository userPreferencesRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.userPreferencesRepository = userPreferencesRepository;
        this.passwordEncoder = passwordEncoder;
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

    @Transactional
    public void updateWalletAddress(UUID userId, String walletAddress) {
        if (walletAddress == null || !walletAddress.matches("^0x[a-fA-F0-9]{40}$")) {
            throw new IllegalArgumentException("Invalid Ethereum wallet address");
        }
        String tenantId = TenantContext.getTenantId();
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setWalletAddress(walletAddress);
        userRepository.save(user);
    }

    @Transactional
    public CurrentUserResponse getCurrentUser(UUID userId) {
        String tenantId = TenantContext.getTenantId();
        User user = getUserForCurrentTenant(userId, tenantId);
        UserProfile profile = getOrCreateProfile(user, tenantId);
        UserPreferences preferences = getOrCreatePreferences(user, tenantId);
        return CurrentUserResponse.from(user, profile, preferences);
    }

    @Transactional
    public CurrentUserResponse updateProfile(UUID userId, UpdateUserProfileRequest request) {
        String tenantId = TenantContext.getTenantId();
        User user = getUserForCurrentTenant(userId, tenantId);
        UserProfile profile = getOrCreateProfile(user, tenantId);

        if (request.fullName() != null) {
            user.setFullName(blankToNull(request.fullName()));
        }
        if (request.headline() != null) {
            profile.setHeadline(blankToNull(request.headline()));
        }
        if (request.bio() != null) {
            profile.setBio(blankToNull(request.bio()));
        }
        if (request.skills() != null) {
            profile.setSkills(normalizeSkills(request.skills()));
        }
        if (request.portfolioUrl() != null) {
            profile.setPortfolioUrl(blankToNull(request.portfolioUrl()));
        }
        if (request.publicProfile() != null) {
            profile.setPublicProfile(request.publicProfile());
        }
        if (request.showEmail() != null) {
            profile.setShowEmail(request.showEmail());
        }
        if (request.showWallet() != null) {
            profile.setShowWallet(request.showWallet());
        }

        userRepository.save(user);
        userProfileRepository.save(profile);
        return CurrentUserResponse.from(user, profile, getOrCreatePreferences(user, tenantId));
    }

    @Transactional
    public CurrentUserResponse updatePreferences(UUID userId, UpdateUserPreferencesRequest request) {
        String tenantId = TenantContext.getTenantId();
        User user = getUserForCurrentTenant(userId, tenantId);
        UserPreferences preferences = getOrCreatePreferences(user, tenantId);

        if (request.theme() != null) preferences.setTheme(request.theme());
        if (request.currency() != null) preferences.setCurrency(request.currency());
        if (request.dateFormat() != null) preferences.setDateFormat(request.dateFormat());
        if (request.timezone() != null) preferences.setTimezone(blankToDefault(request.timezone(), "UTC"));
        if (request.notifyComments() != null) preferences.setNotifyComments(request.notifyComments());
        if (request.notifyTasks() != null) preferences.setNotifyTasks(request.notifyTasks());
        if (request.notifyProjects() != null) preferences.setNotifyProjects(request.notifyProjects());
        if (request.notifyInvoices() != null) preferences.setNotifyInvoices(request.notifyInvoices());
        if (request.quietHoursEnabled() != null) preferences.setQuietHoursEnabled(request.quietHoursEnabled());
        if (request.quietHoursStart() != null) preferences.setQuietHoursStart(request.quietHoursStart());
        if (request.quietHoursEnd() != null) preferences.setQuietHoursEnd(request.quietHoursEnd());

        userPreferencesRepository.save(preferences);
        return CurrentUserResponse.from(user, getOrCreateProfile(user, tenantId), preferences);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        String tenantId = TenantContext.getTenantId();
        User user = getUserForCurrentTenant(userId, tenantId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AccessDeniedException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from the current password");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public boolean allowsNotification(UUID userId, String tenantId, NotificationType type) {
        return userPreferencesRepository.findByUserIdAndTenantId(userId, tenantId)
                .map(preferences -> switch (type) {
                    case NEW_COMMENT, MENTION -> preferences.isNotifyComments();
                    case TASK_ASSIGNED, TASK_COMPLETED -> preferences.isNotifyTasks();
                    case PROJECT_COMPLETED, PROJECT_INVITE -> preferences.isNotifyProjects();
                    case INVOICE_PAID, INVOICE_STATUS_CHANGE -> preferences.isNotifyInvoices();
                })
                .orElse(true);
    }

    private User getUserForCurrentTenant(UUID userId, String tenantId) {
        return userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private UserProfile getOrCreateProfile(User user, String tenantId) {
        return userProfileRepository.findByUserIdAndTenantId(user.getId(), tenantId)
                .orElseGet(() -> {
                    UserProfile profile = new UserProfile();
                    profile.setTenantId(tenantId);
                    profile.setUser(user);
                    return userProfileRepository.save(profile);
                });
    }

    private UserPreferences getOrCreatePreferences(User user, String tenantId) {
        return userPreferencesRepository.findByUserIdAndTenantId(user.getId(), tenantId)
                .orElseGet(() -> {
                    UserPreferences preferences = new UserPreferences();
                    preferences.setTenantId(tenantId);
                    preferences.setUser(user);
                    return userPreferencesRepository.save(preferences);
                });
    }

    private List<String> normalizeSkills(List<String> skills) {
        List<String> normalized = new ArrayList<>();
        for (String skill : skills) {
            String trimmed = blankToNull(skill);
            if (trimmed != null && !normalized.contains(trimmed)) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String blankToDefault(String value, String defaultValue) {
        String trimmed = blankToNull(value);
        return trimmed == null ? defaultValue : trimmed;
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
