package com.clienthub.web.websocket;

import com.clienthub.domain.event.InvoiceStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Component
public class InvoiceStatusNotifier {

    private static final Logger log = LoggerFactory.getLogger(InvoiceStatusNotifier.class);
    
    private final SimpMessagingTemplate messagingTemplate;

    public InvoiceStatusNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public record InvoiceStatusMessage(
            Long id,
            UUID projectId,
            com.clienthub.domain.enums.InvoiceStatus status,
            com.clienthub.domain.enums.EscrowStatus escrowStatus,
            String changeType,
            Instant updatedAt) {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleInvoiceStatusChangedEvent(InvoiceStatusChangedEvent event) {
        log.info("Received InvoiceStatusChangedEvent for invoice {}", event.getInvoice().getId());
        
        // Push to /topic/invoices/{id}/status for clients to subscribe
        String destination = "/topic/invoices/" + event.getInvoice().getId() + "/status";
        
        InvoiceStatusMessage message = new InvoiceStatusMessage(
            event.getInvoice().getId(),
            event.getInvoice().getProject().getId(),
            event.getInvoice().getStatus(),
            event.getInvoice().getEscrowStatus() != null ? event.getInvoice().getEscrowStatus() : com.clienthub.domain.enums.EscrowStatus.NOT_STARTED,
            event.getPreviousStatus() == null ? "CREATED" : "STATUS_CHANGED",
            event.getInvoice().getUpdatedAt()
        );
        
        messagingTemplate.convertAndSend(destination, message);
        Set<UUID> recipients = new LinkedHashSet<>();
        if (event.getInvoice().getClient() != null) {
            recipients.add(event.getInvoice().getClient().getId());
        }
        if (event.getInvoice().getFreelancer() != null) {
            recipients.add(event.getInvoice().getFreelancer().getId());
        }
        for (UUID recipientId : recipients) {
            messagingTemplate.convertAndSend(
                    "/topic/users/" + recipientId + "/invoices",
                    message
            );
        }
        messagingTemplate.convertAndSend(
                "/topic/tenants/" + event.getInvoice().getTenantId() + "/invoices",
                message
        );
        log.info("Sent WebSocket message to {}", destination);
    }
}
