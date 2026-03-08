package com.clienthub.domain.repository;

import com.clienthub.domain.entity.Invoice;
import com.clienthub.domain.enums.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    java.util.List<Invoice> findByTenantIdAndStatus(String tenantId, InvoiceStatus status);

    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Invoice i WHERE i.tenantId = :tenantId AND i.status IN :statuses")
    java.math.BigDecimal sumAmountByTenantIdAndStatuses(
            @Param("tenantId") String tenantId,
            @Param("statuses") java.util.List<InvoiceStatus> statuses
    );
}
