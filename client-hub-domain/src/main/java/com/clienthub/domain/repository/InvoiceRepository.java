package com.clienthub.domain.repository;

import com.clienthub.domain.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByIdAndTenantId(Long id, String tenantId);
    List<Invoice> findByTenantId(String tenantId);
    List<Invoice> findByProjectIdAndTenantId(UUID projectId, String tenantId);

    boolean existsByIdAndTenantId(Long id, String tenantId);
}
