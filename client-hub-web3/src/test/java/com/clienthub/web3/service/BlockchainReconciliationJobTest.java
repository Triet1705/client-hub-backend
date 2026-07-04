package com.clienthub.web3.service;

import com.clienthub.domain.entity.Invoice;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.EscrowStatus;
import com.clienthub.domain.enums.InvoiceStatus;
import com.clienthub.domain.enums.PaymentMethod;
import com.clienthub.domain.enums.Role;
import com.clienthub.domain.repository.InvoiceRepository;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlockchainReconciliationJobTest {
    private static final Long INVOICE_ID = 10L;
    private static final String CONTRACT_ADDRESS = "0x5FbDB2315678afecb367f032d93F642f64180aa3";
    private static final String CLIENT_WALLET = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8";
    private static final String FREELANCER_WALLET = "0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC";
    private static final String TOKEN_ADDRESS = "0x9fE46736679d2D9a65F0992F2272dE9f3c7fa6e0";
    private static final BigInteger DEPOSIT_BLOCK = BigInteger.valueOf(20);

    @Mock
    private BlockchainService blockchainService;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private EscrowValidationService escrowValidationService;

    private BlockchainReconciliationJob reconciliationJob;

    @BeforeEach
    void setUp() {
        reconciliationJob = new BlockchainReconciliationJob(
                blockchainService,
                invoiceRepository,
                eventPublisher,
                escrowValidationService);
        ReflectionTestUtils.setField(reconciliationJob, "blockchainEnabled", true);
        ReflectionTestUtils.setField(reconciliationJob, "contractAddress", CONTRACT_ADDRESS);
        ReflectionTestUtils.setField(reconciliationJob, "requiredConfirmations", 12);
    }

    @Test
    void reconcileEscrowStatuses_WhenDepositHasOneConfirmation_ShouldMarkDepositDetected() {
        Invoice invoice = createInvoice();
        EscrowSnapshot snapshot = createDepositedSnapshot();
        stubActiveInvoice(invoice, snapshot, 1);

        reconciliationJob.reconcileEscrowStatuses();

        assertEquals(EscrowStatus.DEPOSITED, invoice.getEscrowStatus());
        assertEquals(InvoiceStatus.DEPOSIT_DETECTED, invoice.getStatus());
        assertEquals(1, invoice.getConfirmations());
        assertEquals("0xdeposit", invoice.getTxHash());
        assertEquals(CONTRACT_ADDRESS, invoice.getSmartContractId());
        verify(invoiceRepository).save(invoice);
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void reconcileEscrowStatuses_WhenDepositMeetsThreshold_ShouldMarkLocked() {
        Invoice invoice = createInvoice();
        EscrowSnapshot snapshot = createDepositedSnapshot();
        stubActiveInvoice(invoice, snapshot, 12);

        reconciliationJob.reconcileEscrowStatuses();

        assertEquals(EscrowStatus.DEPOSITED, invoice.getEscrowStatus());
        assertEquals(InvoiceStatus.LOCKED, invoice.getStatus());
        assertEquals(12, invoice.getConfirmations());
        verify(invoiceRepository).save(invoice);
    }

    private void stubActiveInvoice(Invoice invoice, EscrowSnapshot snapshot, int confirmations) {
        when(invoiceRepository.findSystemCryptoInvoicesByPaymentMethodAndStatusNotIn(
                PaymentMethod.CRYPTO_ESCROW,
                List.of(InvoiceStatus.PAID, InvoiceStatus.REFUNDED)))
                .thenReturn(List.of(invoice));
        when(blockchainService.getEscrowSnapshot(INVOICE_ID)).thenReturn(Optional.of(snapshot));
        when(escrowValidationService.validate(invoice, snapshot, EscrowStatus.DEPOSITED))
                .thenReturn(EscrowValidationResult.success());
        when(blockchainService.findEventReference(EscrowContractEvents.DEPOSITED_TOPIC, INVOICE_ID))
                .thenReturn(Optional.of(new BlockchainEventReference("0xdeposit", DEPOSIT_BLOCK)));
        when(blockchainService.getConfirmationsSince(DEPOSIT_BLOCK)).thenReturn(confirmations);
    }

    private Invoice createInvoice() {
        User client = User.builder()
                .id(UUID.randomUUID())
                .tenantId("default")
                .email("client@example.com")
                .password("password")
                .fullName("Client")
                .role(Role.CLIENT)
                .walletAddress(CLIENT_WALLET)
                .build();

        Invoice invoice = new Invoice();
        invoice.setId(INVOICE_ID);
        invoice.setTenantId("default");
        invoice.setAmount(BigInteger.valueOf(25));
        invoice.setClient(client);
        invoice.setPaymentMethod(PaymentMethod.CRYPTO_ESCROW);
        invoice.setStatus(InvoiceStatus.CRYPTO_ESCROW_WAITING);
        invoice.setEscrowStatus(EscrowStatus.NOT_STARTED);
        invoice.setWalletAddress(FREELANCER_WALLET);
        invoice.setConfirmations(0);
        return invoice;
    }

    private EscrowSnapshot createDepositedSnapshot() {
        return new EscrowSnapshot(
                CLIENT_WALLET,
                FREELANCER_WALLET,
                TOKEN_ADDRESS,
                BigInteger.valueOf(25).multiply(BigInteger.TEN.pow(18)),
                EscrowStatus.DEPOSITED);
    }
}
