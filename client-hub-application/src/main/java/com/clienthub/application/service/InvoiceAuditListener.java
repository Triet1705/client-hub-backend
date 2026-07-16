package com.clienthub.application.service;

import com.clienthub.domain.entity.Invoice;
import com.clienthub.domain.enums.AuditAction;
import com.clienthub.domain.enums.InvoiceStatus;
import com.clienthub.domain.event.InvoiceStatusChangedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class InvoiceAuditListener {
    private final AuditService auditService;

    public InvoiceAuditListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onInvoiceLifecycleChanged(InvoiceStatusChangedEvent event) {
        Invoice invoice = event.getInvoice();
        InvoiceStatus previousStatus = event.getPreviousStatus();
        InvoiceStatus currentStatus = invoice.getStatus();

        if (previousStatus != null && previousStatus == currentStatus) {
            return;
        }

        AuditAction action = actionFor(previousStatus, currentStatus);
        Map<String, Object> oldValue = previousStatus == null ? null : Map.of("status", previousStatus.name());
        Map<String, Object> newValue = snapshot(invoice);
        auditService.logForTenant(invoice.getTenantId(), action, "INVOICE", String.valueOf(invoice.getId()),
                oldValue, newValue, null);
    }

    private AuditAction actionFor(InvoiceStatus previousStatus, InvoiceStatus currentStatus) {
        if (previousStatus == null) return AuditAction.CREATE;
        if (currentStatus == InvoiceStatus.SENT) return AuditAction.INVOICE_SENT;
        if (currentStatus == InvoiceStatus.PAID) return AuditAction.INVOICE_PAID;
        return AuditAction.UPDATE;
    }

    private Map<String, Object> snapshot(Invoice invoice) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("status", invoice.getStatus() != null ? invoice.getStatus().name() : null);
        value.put("paymentMethod", invoice.getPaymentMethod() != null ? invoice.getPaymentMethod().name() : null);
        value.put("projectId", invoice.getProject() != null ? invoice.getProject().getId() : null);
        value.put("transactionHash", invoice.getTxHash());
        value.put("escrowStatus", invoice.getEscrowStatus() != null ? invoice.getEscrowStatus().name() : null);
        return value;
    }
}
