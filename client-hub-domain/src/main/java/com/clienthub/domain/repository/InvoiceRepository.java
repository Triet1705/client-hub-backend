package com.clienthub.domain.repository;

import com.clienthub.domain.entity.Invoice;
import com.clienthub.domain.enums.InvoiceStatus;
import com.clienthub.domain.enums.PaymentMethod;
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
    
    @Query("SELECT i FROM Invoice i WHERE i.paymentMethod = :paymentMethod AND i.status NOT IN :statuses")
    List<Invoice> findSystemCryptoInvoicesByPaymentMethodAndStatusNotIn(
            @Param("paymentMethod") PaymentMethod paymentMethod,
            @Param("statuses") List<InvoiceStatus> statuses
    );

    @Query("SELECT i FROM Invoice i WHERE i.id = :id AND i.paymentMethod = com.clienthub.domain.enums.PaymentMethod.CRYPTO_ESCROW")
    Optional<Invoice> findSystemCryptoEscrowById(@Param("id") Long id);

    @Query("""
            SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END
            FROM Invoice i
            WHERE i.id = :id
              AND i.tenantId = :tenantId
              AND (i.client.id = :userId OR i.freelancer.id = :userId)
            """)
    boolean existsAccessibleByIdAndTenantIdAndUserId(
            @Param("id") Long id,
            @Param("tenantId") String tenantId,
            @Param("userId") UUID userId
    );

    boolean existsByIdAndTenantId(Long id, String tenantId);
    java.util.List<Invoice> findByTenantIdAndStatus(String tenantId, InvoiceStatus status);
    
    long countByTenantId(String tenantId);
    long countByTenantIdAndStatus(String tenantId, InvoiceStatus status);
    long countByTenantIdAndStatusNot(String tenantId, InvoiceStatus status);

    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Invoice i WHERE i.tenantId = :tenantId AND i.status IN :statuses")
    java.math.BigDecimal sumAmountByTenantIdAndStatuses(
            @Param("tenantId") String tenantId,
            @Param("statuses") java.util.List<InvoiceStatus> statuses
    );

    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Invoice i WHERE i.status IN :statuses")
    java.math.BigDecimal sumAmountByStatuses(
            @Param("statuses") java.util.List<InvoiceStatus> statuses
    );
}
