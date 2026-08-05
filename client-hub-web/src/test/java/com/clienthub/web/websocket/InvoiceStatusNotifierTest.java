package com.clienthub.web.websocket;

import com.clienthub.domain.entity.Invoice;
import com.clienthub.domain.entity.Project;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.EscrowStatus;
import com.clienthub.domain.enums.InvoiceStatus;
import com.clienthub.domain.enums.Role;
import com.clienthub.domain.event.InvoiceStatusChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InvoiceStatusNotifierTest {

    @Test
    void invoiceChangePublishesDetailUserAndTenantMessages() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        InvoiceStatusNotifier notifier = new InvoiceStatusNotifier(messagingTemplate);
        UUID projectId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID freelancerId = UUID.randomUUID();

        User client = new User(clientId, "default", "client@example.test", "hash",
                "Client", Role.CLIENT, true, null);
        User freelancer = new User(freelancerId, "default", "freelancer@example.test", "hash",
                "Freelancer", Role.FREELANCER, true, null);
        Project project = new Project();
        project.setId(projectId);

        Invoice invoice = new Invoice();
        invoice.setId(35L);
        invoice.setTenantId("default");
        invoice.setProject(project);
        invoice.setClient(client);
        invoice.setFreelancer(freelancer);
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setEscrowStatus(EscrowStatus.RELEASED);

        notifier.handleInvoiceStatusChangedEvent(new InvoiceStatusChangedEvent(
                this, invoice, InvoiceStatus.LOCKED));

        verify(messagingTemplate).convertAndSend(
                (String) eq("/topic/invoices/35/status"),
                (Object) argThat(message -> message instanceof InvoiceStatusNotifier.InvoiceStatusMessage changed
                        && changed.id().equals(35L)
                        && changed.projectId().equals(projectId)
                        && changed.status() == InvoiceStatus.PAID));
        verify(messagingTemplate).convertAndSend(
                (String) eq("/topic/users/" + clientId + "/invoices"),
                (Object) argThat(message -> message instanceof InvoiceStatusNotifier.InvoiceStatusMessage));
        verify(messagingTemplate).convertAndSend(
                (String) eq("/topic/users/" + freelancerId + "/invoices"),
                (Object) argThat(message -> message instanceof InvoiceStatusNotifier.InvoiceStatusMessage));
        verify(messagingTemplate).convertAndSend(
                (String) eq("/topic/tenants/default/invoices"),
                (Object) argThat(message -> message instanceof InvoiceStatusNotifier.InvoiceStatusMessage));
    }
}
