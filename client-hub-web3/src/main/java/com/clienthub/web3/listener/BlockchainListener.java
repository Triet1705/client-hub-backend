package com.clienthub.web3.listener;

import com.clienthub.common.context.TenantContext;
import com.clienthub.domain.entity.Invoice;
import com.clienthub.domain.enums.EscrowStatus;
import com.clienthub.domain.enums.InvoiceStatus;
import com.clienthub.domain.event.InvoiceStatusChangedEvent;
import com.clienthub.domain.repository.InvoiceRepository;
import com.clienthub.web3.service.BlockchainService;
import com.clienthub.web3.service.EscrowContractEvents;
import com.clienthub.web3.service.EscrowSnapshot;
import com.clienthub.web3.service.EscrowValidationResult;
import com.clienthub.web3.service.EscrowValidationService;
import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.utils.Numeric;

@Component
public class BlockchainListener {
    private static final Logger log = LoggerFactory.getLogger(BlockchainListener.class);

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

    public BlockchainListener(BlockchainService blockchainService,
                              InvoiceRepository invoiceRepository,
                              ApplicationEventPublisher eventPublisher,
                              EscrowValidationService escrowValidationService) {
        this.blockchainService = blockchainService;
        this.invoiceRepository = invoiceRepository;
        this.eventPublisher = eventPublisher;
        this.escrowValidationService = escrowValidationService;
    }

    @PostConstruct
    public void subscribeToEvents() {
        if (!blockchainEnabled || contractAddress == null || contractAddress.isEmpty()) {
            return;
        }

        EthFilter filter = new EthFilter(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST, contractAddress);
        filter.addOptionalTopics(
                EscrowContractEvents.DEPOSITED_TOPIC,
                EscrowContractEvents.RELEASED_TOPIC,
                EscrowContractEvents.REFUNDED_TOPIC);

        blockchainService.getWeb3j().ethLogFlowable(filter).subscribe(logEvent -> {
            TenantContext.setSystemContext();
            try {
                handleEscrowLog(logEvent);
            } finally {
                TenantContext.clear();
            }
        }, error -> log.error("Error subscribing to blockchain events", error));
    }

    private void handleEscrowLog(Log logEvent) {
        if (logEvent.getTopics().size() < 2) {
            log.warn("Ignoring escrow event without indexed invoiceId: {}", logEvent.getTransactionHash());
            return;
        }

        String topic = logEvent.getTopics().get(0);
        Long invoiceId = Numeric.decodeQuantity(logEvent.getTopics().get(1)).longValue();
        EscrowStatus expectedStatus = expectedStatusForTopic(topic);
        if (expectedStatus == null) {
            log.warn("Ignoring unsupported escrow event topic {} for invoice {}", topic, invoiceId);
            return;
        }

        Optional<Invoice> optInvoice = invoiceRepository.findSystemCryptoEscrowById(invoiceId);
        if (optInvoice.isEmpty()) {
            log.warn("Ignoring escrow event for unknown crypto invoice {}", invoiceId);
            return;
        }

        Optional<EscrowSnapshot> optSnapshot = blockchainService.getEscrowSnapshot(invoiceId);
        if (optSnapshot.isEmpty()) {
            log.warn("Ignoring escrow event for invoice {} because the chain snapshot could not be read", invoiceId);
            return;
        }

        Invoice invoice = optInvoice.get();
        EscrowValidationResult validation = escrowValidationService.validate(invoice, optSnapshot.get(), expectedStatus);
        if (!validation.valid()) {
            log.warn("Ignoring escrow event for invoice {}: {}", invoiceId, validation.reason());
            return;
        }

        InvoiceStatus previousStatus = invoice.getStatus();
        if (applyValidatedEvent(invoice, expectedStatus, logEvent.getTransactionHash(), logEvent.getBlockNumber())) {
            invoiceRepository.save(invoice);
            eventPublisher.publishEvent(new InvoiceStatusChangedEvent(this, invoice, previousStatus));
        }
    }

    private EscrowStatus expectedStatusForTopic(String topic) {
        if (EscrowContractEvents.DEPOSITED_TOPIC.equals(topic)) {
            return EscrowStatus.DEPOSITED;
        }
        if (EscrowContractEvents.RELEASED_TOPIC.equals(topic)) {
            return EscrowStatus.RELEASED;
        }
        if (EscrowContractEvents.REFUNDED_TOPIC.equals(topic)) {
            return EscrowStatus.REFUNDED;
        }
        return null;
    }

    private boolean applyValidatedEvent(Invoice invoice, EscrowStatus status, String txHash, BigInteger blockNumber) {
        InvoiceStatus previousStatus = invoice.getStatus();
        EscrowStatus previousEscrowStatus = invoice.getEscrowStatus();
        String previousTxHash = invoice.getTxHash();
        Integer previousConfirmations = invoice.getConfirmations();
        Instant previousPaidAt = invoice.getPaidAt();

        invoice.setTxHash(txHash);
        invoice.setSmartContractId(contractAddress);

        if (status == EscrowStatus.DEPOSITED) {
            int confirmations = blockchainService.getConfirmationsSince(blockNumber);
            invoice.setEscrowStatus(EscrowStatus.DEPOSITED);
            invoice.setConfirmations(confirmations);
            invoice.setStatus(confirmations >= effectiveRequiredConfirmations()
                    ? InvoiceStatus.LOCKED
                    : InvoiceStatus.DEPOSIT_DETECTED);
        } else if (status == EscrowStatus.RELEASED) {
            setMaxConfirmations(invoice, blockNumber);
            invoice.setEscrowStatus(EscrowStatus.RELEASED);
            invoice.setStatus(InvoiceStatus.PAID);
            if (invoice.getPaidAt() == null) {
                invoice.setPaidAt(Instant.now());
            }
        } else if (status == EscrowStatus.REFUNDED) {
            setMaxConfirmations(invoice, blockNumber);
            invoice.setEscrowStatus(EscrowStatus.REFUNDED);
            invoice.setStatus(InvoiceStatus.REFUNDED);
        }

        return previousStatus != invoice.getStatus()
                || previousEscrowStatus != invoice.getEscrowStatus()
                || !Objects.equals(previousTxHash, invoice.getTxHash())
                || !Objects.equals(previousConfirmations, invoice.getConfirmations())
                || !Objects.equals(previousPaidAt, invoice.getPaidAt());
    }

    private void setMaxConfirmations(Invoice invoice, BigInteger blockNumber) {
        int confirmations = blockchainService.getConfirmationsSince(blockNumber);
        int existingConfirmations = invoice.getConfirmations() != null ? invoice.getConfirmations() : 0;
        if (confirmations > existingConfirmations) {
            invoice.setConfirmations(confirmations);
        }
    }

    private int effectiveRequiredConfirmations() {
        return Math.max(1, requiredConfirmations);
    }
}
