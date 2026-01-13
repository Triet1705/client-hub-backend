package com.clienthub.core.mapper;

import com.clienthub.core.domain.entity.Invoice;
import com.clienthub.core.dto.invoice.InvoiceRequest;
import com.clienthub.core.dto.invoice.InvoiceResponse;
import org.springframework.stereotype.Component;

@Component
public class InvoiceMapperImpl implements InvoiceMapper {

    @Override
    public InvoiceResponse toResponse(Invoice invoice) {
        if (invoice == null) {
            return null;
        }

        InvoiceResponse response = new InvoiceResponse();
        response.setId(invoice.getId());
        response.setTitle(invoice.getTitle());
        response.setAmount(invoice.getAmount());
        response.setDueDate(invoice.getDueDate());
        response.setStatus(invoice.getStatus());
        response.setPaymentMethod(invoice.getPaymentMethod());
        response.setSmartContractId(invoice.getSmartContractId());
        response.setTxHash(invoice.getTxHash());
        response.setWalletAddress(invoice.getWalletAddress());
        response.setEscrowStatus(invoice.getEscrowStatus());
        response.setConfirmations(invoice.getConfirmations());

        if (invoice.getProject() != null) {
            response.setProjectId(invoice.getProject().getId());
        }
        if (invoice.getClient() != null) {
            response.setClientId(invoice.getClient().getId());
        }
        if (invoice.getFreelancer() != null) {
            response.setFreelancerId(invoice.getFreelancer().getId());
        }

        if (invoice.getCreatedAt() != null) {
            response.setCreatedAt(invoice.getCreatedAt().toInstant(java.time.ZoneOffset.UTC));
        }
        if (invoice.getUpdateAt() != null) {
            response.setUpdatedAt(invoice.getUpdateAt().toInstant(java.time.ZoneOffset.UTC));
        }

        return response;
    }

    @Override
    public Invoice toEntity(InvoiceRequest request) {
        if (request == null) {
            return null;
        }

        Invoice invoice = new Invoice();
        invoice.setTitle(request.getTitle());
        invoice.setAmount(request.getAmount());
        invoice.setDueDate(request.getDueDate());
        
        if (request.getPaymentMethod() != null) {
            invoice.setPaymentMethod(request.getPaymentMethod());
        }
        if (request.getClientWalletAddress() != null) {
            invoice.setWalletAddress(request.getClientWalletAddress());
        }

        return invoice;
    }

    @Override
    public void updateEntityFromRequest(InvoiceRequest request, Invoice invoice) {
        if (request == null || invoice == null) {
            return;
        }

        if (request.getTitle() != null) {
            invoice.setTitle(request.getTitle());
        }
        if (request.getAmount() != null) {
            invoice.setAmount(request.getAmount());
        }
        if (request.getDueDate() != null) {
            invoice.setDueDate(request.getDueDate());
        }
        if (request.getPaymentMethod() != null) {
            invoice.setPaymentMethod(request.getPaymentMethod());
        }
        if (request.getClientWalletAddress() != null) {
            invoice.setWalletAddress(request.getClientWalletAddress());
        }
    }
}
