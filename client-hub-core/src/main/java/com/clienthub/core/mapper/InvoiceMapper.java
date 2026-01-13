package com.clienthub.core.mapper;

import com.clienthub.core.domain.entity.Invoice;
import com.clienthub.core.dto.invoice.InvoiceRequest;
import com.clienthub.core.dto.invoice.InvoiceResponse;

public interface InvoiceMapper {
    InvoiceResponse toResponse(Invoice invoice);
    Invoice toEntity(InvoiceRequest request);
    void updateEntityFromRequest(InvoiceRequest request, Invoice invoice);
}