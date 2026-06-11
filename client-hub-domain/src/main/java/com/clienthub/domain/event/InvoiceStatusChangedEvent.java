package com.clienthub.domain.event;

import com.clienthub.domain.entity.Invoice;
import org.springframework.context.ApplicationEvent;

public class InvoiceStatusChangedEvent extends ApplicationEvent {
    
    private final Invoice invoice;

    public InvoiceStatusChangedEvent(Object source, Invoice invoice) {
        super(source);
        this.invoice = invoice;
    }

    public Invoice getInvoice() {
        return invoice;
    }
}
