package com.clienthub.domain.repository;

import com.clienthub.domain.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    Optional<UserProfile> findByUserIdAndTenantId(UUID userId, String tenantId);
}
