package com.clienthub.application.dto.admin;

import com.clienthub.domain.entity.Invoice;
import com.clienthub.domain.enums.InvoiceStatus;

import java.math.BigInteger;
import java.time.Instant;

public record AdminInvoiceResponse(
        Long id,
        BigInteger amount,
        InvoiceStatus status,
        String tenantId,
        String projectTitle,
        String createdByEmail,
        Instant createdAt
) {
    public static AdminInvoiceResponse from(Invoice invoice) {
        return new AdminInvoiceResponse(
                invoice.getId(),
                invoice.getAmount(),
                invoice.getStatus(),
                invoice.getTenantId(),
                invoice.getProject() != null ? invoice.getProject().getTitle() : null,
                invoice.getCreatedBy(),
                invoice.getCreatedAt()
        );
    }
}
