package com.clienthub.domain.event;

import com.clienthub.domain.entity.Invoice;
import com.clienthub.domain.enums.InvoiceStatus;
import org.springframework.context.ApplicationEvent;

public class InvoiceStatusChangedEvent extends ApplicationEvent {
    
    private final Invoice invoice;
    private final InvoiceStatus previousStatus;

    public InvoiceStatusChangedEvent(Object source, Invoice invoice, InvoiceStatus previousStatus) {
        super(source);
        this.invoice = invoice;
        this.previousStatus = previousStatus;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public InvoiceStatus getPreviousStatus() {
        return previousStatus;
    }
}
