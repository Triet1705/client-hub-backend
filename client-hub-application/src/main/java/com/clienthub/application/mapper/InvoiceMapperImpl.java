package com.clienthub.application.mapper;

import com.clienthub.domain.entity.Invoice;
import com.clienthub.application.dto.invoice.InvoiceRequest;
import com.clienthub.application.dto.invoice.InvoiceResponse;
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
        response.setDescription(invoice.getDescription());
        response.setAmount(invoice.getAmount());
        response.setDueDate(invoice.getDueDate());
        response.setStatus(invoice.getStatus());
        response.setPaymentMethod(invoice.getPaymentMethod());
        response.setSmartContractId(invoice.getSmartContractId());
        response.setTxHash(invoice.getTxHash());
        response.setWalletAddress(invoice.getWalletAddress());
        response.setEscrowStatus(invoice.getEscrowStatus());
        response.setConfirmations(invoice.getConfirmations());
        response.setPaidAt(invoice.getPaidAt());

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
            response.setCreatedAt(invoice.getCreatedAt());
        }
        if (invoice.getUpdatedAt() != null) {
            response.setUpdatedAt(invoice.getUpdatedAt());
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
        invoice.setDescription(request.getDescription());
        invoice.setAmount(request.getAmount());
        invoice.setDueDate(request.getDueDate());
        
        if (request.getPaymentMethod() != null) {
            invoice.setPaymentMethod(request.getPaymentMethod());
        }
        if (request.getFreelancerWalletAddress() != null) {
            invoice.setWalletAddress(request.getFreelancerWalletAddress());
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
        if (request.getDescription() != null) {
            invoice.setDescription(request.getDescription());
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
        if (request.getFreelancerWalletAddress() != null) {
            invoice.setWalletAddress(request.getFreelancerWalletAddress());
        }
    }
}
