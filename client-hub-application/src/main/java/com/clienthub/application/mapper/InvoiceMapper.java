package com.clienthub.application.mapper;

import com.clienthub.domain.entity.Invoice;
import com.clienthub.application.dto.invoice.InvoiceRequest;
import com.clienthub.application.dto.invoice.InvoiceResponse;

public interface InvoiceMapper {
    InvoiceResponse toResponse(Invoice invoice);
    Invoice toEntity(InvoiceRequest request);
    void updateEntityFromRequest(InvoiceRequest request, Invoice invoice);
}