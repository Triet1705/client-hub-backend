package com.clienthub.domain.repository;

import com.clienthub.domain.entity.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    List<Certificate> findByUserIdAndTenantId(UUID userId, String tenantId);
    Optional<Certificate> findByProjectIdAndUserIdAndTenantId(UUID projectId, UUID userId, String tenantId);
}
