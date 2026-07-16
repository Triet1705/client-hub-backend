package com.clienthub.web3.service;

import com.clienthub.common.context.TenantContext;
import com.clienthub.domain.entity.Invoice;
import com.clienthub.domain.enums.EscrowStatus;
import com.clienthub.domain.enums.InvoiceStatus;
import com.clienthub.domain.enums.PaymentMethod;
import com.clienthub.domain.event.InvoiceStatusChangedEvent;
import com.clienthub.domain.repository.InvoiceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BlockchainReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(BlockchainReconciliationJob.class);

    private final BlockchainService blockchainService;
    private final InvoiceRepository invoiceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EscrowValidationService escrowValidationService;

    @Value("${blockchain.enabled:false}")
    private boolean blockchainEnabled;

    @Value("${blockchain.contract_address:}")
    private String contractAddress;

    @Value("${blockchain.required_confirmations:12}")
    private int requiredConfirmations;

    public BlockchainReconciliationJob(BlockchainService blockchainService,
                                       InvoiceRepository invoiceRepository,
                                       ApplicationEventPublisher eventPublisher,
                                       EscrowValidationService escrowValidationService) {
        this.blockchainService = blockchainService;
        this.invoiceRepository = invoiceRepository;
        this.eventPublisher = eventPublisher;
        this.escrowValidationService = escrowValidationService;
    }

    @Scheduled(fixedDelayString = "${blockchain.reconciliation_delay_ms:300000}")
    public void reconcileEscrowStatuses() {
        if (!blockchainEnabled) {
            return;
        }

        TenantContext.setSystemContext();
        try {
            log.info("Starting blockchain reconciliation job for escrow invoices...");

            List<Invoice> activeInvoices = invoiceRepository.findSystemCryptoInvoicesByPaymentMethodAndStatusNotIn(
                    PaymentMethod.CRYPTO_ESCROW,
                    List.of(InvoiceStatus.PAID, InvoiceStatus.REFUNDED)
            );

            for (Invoice invoice : activeInvoices) {
                reconcileInvoice(invoice);
            }

            log.info("Finished blockchain reconciliation job.");
        } finally {
            TenantContext.clear();
        }
    }

    private void reconcileInvoice(Invoice invoice) {
        Optional<EscrowSnapshot> optSnapshot = blockchainService.getEscrowSnapshot(invoice.getId());
        if (optSnapshot.isEmpty()) {
            log.warn("Skipping invoice {} reconciliation because escrow snapshot could not be read", invoice.getId());
            return;
        }

        EscrowSnapshot snapshot = optSnapshot.get();
        if (snapshot.status() == EscrowStatus.NOT_STARTED) {
            return;
        }

        EscrowValidationResult validation = escrowValidationService.validate(invoice, snapshot, snapshot.status());
        if (!validation.valid()) {
            log.warn("Skipping invoice {} reconciliation: {}", invoice.getId(), validation.reason());
            return;
        }

        InvoiceStatus previousStatus = invoice.getStatus();
        if (applyValidatedSnapshot(invoice, snapshot.status())) {
            invoiceRepository.save(invoice);
            eventPublisher.publishEvent(new InvoiceStatusChangedEvent(this, invoice, previousStatus));
        }
    }

    private boolean applyValidatedSnapshot(Invoice invoice, EscrowStatus onChainStatus) {
        InvoiceStatus previousStatus = invoice.getStatus();
        EscrowStatus previousEscrowStatus = invoice.getEscrowStatus();
        String previousTxHash = invoice.getTxHash();
        Integer previousConfirmations = invoice.getConfirmations();
        Instant previousPaidAt = invoice.getPaidAt();
        String previousSmartContractId = invoice.getSmartContractId();

        invoice.setSmartContractId(contractAddress);

        if (onChainStatus == EscrowStatus.DEPOSITED) {
            applyDepositedSnapshot(invoice);
        } else if (onChainStatus == EscrowStatus.RELEASED) {
            applyTerminalSnapshot(invoice, EscrowStatus.RELEASED, InvoiceStatus.PAID, EscrowContractEvents.RELEASED_TOPIC);
        } else if (onChainStatus == EscrowStatus.REFUNDED) {
            applyTerminalSnapshot(invoice, EscrowStatus.REFUNDED, InvoiceStatus.REFUNDED, EscrowContractEvents.REFUNDED_TOPIC);
        }

        return previousStatus != invoice.getStatus()
                || previousEscrowStatus != invoice.getEscrowStatus()
                || !Objects.equals(previousTxHash, invoice.getTxHash())
                || !Objects.equals(previousConfirmations, invoice.getConfirmations())
                || !Objects.equals(previousPaidAt, invoice.getPaidAt())
                || !Objects.equals(previousSmartContractId, invoice.getSmartContractId());
    }

    private void applyDepositedSnapshot(Invoice invoice) {
        Optional<BlockchainEventReference> reference = existingTransactionReference(invoice)
                .or(() -> blockchainService.findEventReference(EscrowContractEvents.DEPOSITED_TOPIC, invoice.getId()));
        int confirmations = reference
                .map(BlockchainEventReference::blockNumber)
                .map(blockchainService::getConfirmationsSince)
                .orElse(invoice.getConfirmations() != null ? invoice.getConfirmations() : 0);

        reference.map(BlockchainEventReference::transactionHash).ifPresent(invoice::setTxHash);
        invoice.setEscrowStatus(EscrowStatus.DEPOSITED);
        invoice.setConfirmations(confirmations);
        if (confirmations >= effectiveRequiredConfirmations()) {
            invoice.setStatus(InvoiceStatus.LOCKED);
        } else if (invoice.getStatus() == InvoiceStatus.CRYPTO_ESCROW_WAITING) {
            invoice.setStatus(InvoiceStatus.DEPOSIT_DETECTED);
        }
    }

    private void applyTerminalSnapshot(Invoice invoice, EscrowStatus escrowStatus, InvoiceStatus invoiceStatus, String eventTopic) {
        Optional<BlockchainEventReference> reference = blockchainService.findEventReference(eventTopic, invoice.getId())
                .or(() -> existingTransactionReference(invoice));
        reference.map(BlockchainEventReference::transactionHash).ifPresent(invoice::setTxHash);
        reference.map(BlockchainEventReference::blockNumber)
                .map(blockchainService::getConfirmationsSince)
                .ifPresent(confirmations -> {
                    int existingConfirmations = invoice.getConfirmations() != null ? invoice.getConfirmations() : 0;
                    if (confirmations > existingConfirmations) {
                        invoice.setConfirmations(confirmations);
                    }
                });

        invoice.setEscrowStatus(escrowStatus);
        invoice.setStatus(invoiceStatus);
        if (invoiceStatus == InvoiceStatus.PAID && invoice.getPaidAt() == null) {
            invoice.setPaidAt(Instant.now());
        }
    }

    private Optional<BlockchainEventReference> existingTransactionReference(Invoice invoice) {
        String txHash = invoice.getTxHash();
        if (txHash == null || txHash.isBlank()) {
            return Optional.empty();
        }

        return blockchainService.getTransactionBlockNumber(txHash)
                .map(blockNumber -> new BlockchainEventReference(txHash, blockNumber));
    }

    private int effectiveRequiredConfirmations() {
        return Math.max(1, requiredConfirmations);
    }
}
