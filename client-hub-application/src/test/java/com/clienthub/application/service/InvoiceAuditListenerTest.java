package com.clienthub.application.service;

import com.clienthub.domain.entity.Invoice;
import com.clienthub.domain.entity.Project;
import com.clienthub.domain.enums.AuditAction;
import com.clienthub.domain.enums.InvoiceStatus;
import com.clienthub.domain.enums.PaymentMethod;
import com.clienthub.domain.event.InvoiceStatusChangedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InvoiceAuditListenerTest {
    @Mock private AuditService auditService;

    @Test
    void lifecycleEvents_MapCreateSentAndPaidActions() {
        InvoiceAuditListener listener = new InvoiceAuditListener(auditService);
        Invoice invoice = invoice(InvoiceStatus.DRAFT);
        listener.onInvoiceLifecycleChanged(new InvoiceStatusChangedEvent(this, invoice, null));
        verify(auditService).logForTenant(eq("default"), eq(AuditAction.CREATE), eq("INVOICE"), eq("7"),
                isNull(), any(Map.class), isNull());

        invoice.setStatus(InvoiceStatus.SENT);
        listener.onInvoiceLifecycleChanged(new InvoiceStatusChangedEvent(this, invoice, InvoiceStatus.DRAFT));
        verify(auditService).logForTenant(eq("default"), eq(AuditAction.INVOICE_SENT), eq("INVOICE"), eq("7"),
                any(Map.class), any(Map.class), isNull());

        invoice.setStatus(InvoiceStatus.PAID);
        listener.onInvoiceLifecycleChanged(new InvoiceStatusChangedEvent(this, invoice, InvoiceStatus.SENT));
        verify(auditService).logForTenant(eq("default"), eq(AuditAction.INVOICE_PAID), eq("INVOICE"), eq("7"),
                any(Map.class), any(Map.class), isNull());
    }

    @Test
    void lifecycleEvent_WhenStatusDidNotChange_DoesNotAudit() {
        InvoiceAuditListener listener = new InvoiceAuditListener(auditService);
        Invoice invoice = invoice(InvoiceStatus.LOCKED);

        listener.onInvoiceLifecycleChanged(new InvoiceStatusChangedEvent(this, invoice, InvoiceStatus.LOCKED));

        verify(auditService, never()).logForTenant(anyString(), any(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void lifecycleEvent_IncludesPaymentAndChainContext() {
        InvoiceAuditListener listener = new InvoiceAuditListener(auditService);
        Invoice invoice = invoice(InvoiceStatus.PAID);
        invoice.setTxHash("0xabc");

        listener.onInvoiceLifecycleChanged(new InvoiceStatusChangedEvent(this, invoice, InvoiceStatus.LOCKED));

        ArgumentCaptor<Object> value = ArgumentCaptor.forClass(Object.class);
        verify(auditService).logForTenant(eq("default"), eq(AuditAction.INVOICE_PAID), eq("INVOICE"), eq("7"),
                any(), value.capture(), isNull());
        Map<String, Object> snapshot = (Map<String, Object>) value.getValue();
        assertEquals("CRYPTO_ESCROW", snapshot.get("paymentMethod"));
        assertEquals("0xabc", snapshot.get("transactionHash"));
    }

    private Invoice invoice(InvoiceStatus status) {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        Invoice invoice = new Invoice();
        invoice.setId(7L);
        invoice.setTenantId("default");
        invoice.setProject(project);
        invoice.setPaymentMethod(PaymentMethod.CRYPTO_ESCROW);
        invoice.setStatus(status);
        return invoice;
    }
}
