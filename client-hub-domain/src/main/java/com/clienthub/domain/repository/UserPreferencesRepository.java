package com.clienthub.domain.repository;

import com.clienthub.domain.entity.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserPreferencesRepository extends JpaRepository<UserPreferences, Long> {
    Optional<UserPreferences> findByUserIdAndTenantId(UUID userId, String tenantId);
}
