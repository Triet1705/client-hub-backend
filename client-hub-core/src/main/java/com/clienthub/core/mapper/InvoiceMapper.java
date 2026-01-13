package com.clienthub.core.mapper;

import com.clienthub.core.domain.entity.Invoice;
import com.clienthub.core.dto.invoice.InvoiceRequest;
import com.clienthub.core.dto.invoice.InvoiceResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface InvoiceMapper {

    @Mapping(source = "project.id", target = "projectId")
    @Mapping(source = "client.id", target = "clientId")
    @Mapping(source = "freelancer.id", target = "freelancerId")
    InvoiceResponse toResponse(Invoice invoice);

    Invoice toEntity(InvoiceRequest request);

    void updateEntityFromRequest(InvoiceRequest request, @MappingTarget Invoice invoice);
}