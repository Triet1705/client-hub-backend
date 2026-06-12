package com.clienthub.web.websocket;

import com.clienthub.domain.event.InvoiceStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class InvoiceStatusNotifier {

    private static final Logger log = LoggerFactory.getLogger(InvoiceStatusNotifier.class);
    
    private final SimpMessagingTemplate messagingTemplate;

    public InvoiceStatusNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public record InvoiceStatusMessage(Long id, com.clienthub.domain.enums.InvoiceStatus status, com.clienthub.domain.enums.EscrowStatus escrowStatus) {}

    @EventListener
    public void handleInvoiceStatusChangedEvent(InvoiceStatusChangedEvent event) {
        log.info("Received InvoiceStatusChangedEvent for invoice {}", event.getInvoice().getId());
        
        // Push to /topic/invoices/{id}/status for clients to subscribe
        String destination = "/topic/invoices/" + event.getInvoice().getId() + "/status";
        
        InvoiceStatusMessage message = new InvoiceStatusMessage(
            event.getInvoice().getId(),
            event.getInvoice().getStatus(),
            event.getInvoice().getEscrowStatus() != null ? event.getInvoice().getEscrowStatus() : com.clienthub.domain.enums.EscrowStatus.NOT_STARTED
        );
        
        messagingTemplate.convertAndSend(destination, message);
        log.info("Sent WebSocket message to {}", destination);
    }
}
