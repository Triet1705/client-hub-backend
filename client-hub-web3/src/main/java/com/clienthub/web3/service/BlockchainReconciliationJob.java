package com.clienthub.web3.service;

import com.clienthub.common.context.TenantContext;
import com.clienthub.domain.entity.Invoice;
import com.clienthub.domain.enums.EscrowStatus;
import com.clienthub.domain.enums.InvoiceStatus;
import com.clienthub.domain.enums.PaymentMethod;
import com.clienthub.domain.repository.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class BlockchainReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(BlockchainReconciliationJob.class);

    private final BlockchainService blockchainService;
    private final InvoiceRepository invoiceRepository;

    @Value("${blockchain.enabled:false}")
    private boolean blockchainEnabled;

    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public BlockchainReconciliationJob(BlockchainService blockchainService, 
                                       InvoiceRepository invoiceRepository,
                                       org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.blockchainService = blockchainService;
        this.invoiceRepository = invoiceRepository;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelay = 300000) // 5 minutes
    public void reconcileEscrowStatuses() {
        if (!blockchainEnabled) {
            return;
        }

        TenantContext.setSystemContext();
        try {
            log.info("Starting blockchain reconciliation job for escrow invoices...");

            // Find all crypto invoices that are not in terminal states
        List<Invoice> activeInvoices = invoiceRepository.findSystemCryptoInvoicesByPaymentMethodAndStatusNotIn(
                PaymentMethod.CRYPTO_ESCROW, 
                List.of(InvoiceStatus.PAID, InvoiceStatus.REFUNDED)
        );

        for (Invoice invoice : activeInvoices) {
            EscrowStatus onChainStatus = blockchainService.getEscrowStatus(invoice.getId());
            
            boolean updated = false;

            if (onChainStatus != invoice.getEscrowStatus()) {
                log.info("Mismatch found for Invoice ID {}. DB: {}, Chain: {}. Correcting...", 
                        invoice.getId(), invoice.getEscrowStatus(), onChainStatus);
                
                invoice.setEscrowStatus(onChainStatus);
                updated = true;

                // Sync InvoiceStatus based on EscrowStatus
                if (onChainStatus == EscrowStatus.DEPOSITED && invoice.getStatus() != InvoiceStatus.LOCKED) {
                    invoice.setStatus(InvoiceStatus.LOCKED);
                } else if (onChainStatus == EscrowStatus.RELEASED) {
                    invoice.setStatus(InvoiceStatus.PAID);
                    invoice.setPaidAt(Instant.now());
                } else if (onChainStatus == EscrowStatus.REFUNDED) {
                    invoice.setStatus(InvoiceStatus.REFUNDED);
                }
            }

            if (updated) {
                invoiceRepository.save(invoice);
                eventPublisher.publishEvent(new com.clienthub.domain.event.InvoiceStatusChangedEvent(this, invoice));
            }
        }
        
        log.info("Finished blockchain reconciliation job.");
        } finally {
            TenantContext.clear();
        }
    }
}
