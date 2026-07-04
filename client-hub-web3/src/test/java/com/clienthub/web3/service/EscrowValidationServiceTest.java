package com.clienthub.web3.service;

import com.clienthub.domain.entity.Invoice;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.EscrowStatus;
import com.clienthub.domain.enums.PaymentMethod;
import com.clienthub.domain.enums.Role;
import java.math.BigInteger;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowValidationServiceTest {
    private static final String CLIENT_WALLET = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8";
    private static final String FREELANCER_WALLET = "0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC";
    private static final String TOKEN_ADDRESS = "0x9fE46736679d2D9a65F0992F2272dE9f3c7fa6e0";
    private static final BigInteger RAW_AMOUNT = BigInteger.valueOf(25).multiply(BigInteger.TEN.pow(18));

    private EscrowValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new EscrowValidationService();
        ReflectionTestUtils.setField(validationService, "tokenAddress", TOKEN_ADDRESS);
        ReflectionTestUtils.setField(validationService, "tokenDecimals", 18);
    }

    @Test
    void validate_WhenSnapshotMatchesInvoice_ShouldPass() {
        EscrowValidationResult result = validationService.validate(
                createInvoice(),
                createSnapshot(CLIENT_WALLET, FREELANCER_WALLET, TOKEN_ADDRESS, RAW_AMOUNT),
                EscrowStatus.DEPOSITED);

        assertTrue(result.valid());
    }

    @Test
    void validate_WhenTokenDiffers_ShouldReject() {
        EscrowValidationResult result = validationService.validate(
                createInvoice(),
                createSnapshot(CLIENT_WALLET, FREELANCER_WALLET, "0x0000000000000000000000000000000000000001", RAW_AMOUNT),
                EscrowStatus.DEPOSITED);

        assertFalse(result.valid());
    }

    @Test
    void validate_WhenAmountDiffers_ShouldReject() {
        EscrowValidationResult result = validationService.validate(
                createInvoice(),
                createSnapshot(CLIENT_WALLET, FREELANCER_WALLET, TOKEN_ADDRESS, RAW_AMOUNT.subtract(BigInteger.ONE)),
                EscrowStatus.DEPOSITED);

        assertFalse(result.valid());
    }

    @Test
    void validate_WhenClientDiffers_ShouldReject() {
        EscrowValidationResult result = validationService.validate(
                createInvoice(),
                createSnapshot("0x0000000000000000000000000000000000000002", FREELANCER_WALLET, TOKEN_ADDRESS, RAW_AMOUNT),
                EscrowStatus.DEPOSITED);

        assertFalse(result.valid());
    }

    @Test
    void validate_WhenFreelancerDiffers_ShouldReject() {
        EscrowValidationResult result = validationService.validate(
                createInvoice(),
                createSnapshot(CLIENT_WALLET, "0x0000000000000000000000000000000000000003", TOKEN_ADDRESS, RAW_AMOUNT),
                EscrowStatus.DEPOSITED);

        assertFalse(result.valid());
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
        invoice.setId(10L);
        invoice.setTenantId("default");
        invoice.setAmount(BigInteger.valueOf(25));
        invoice.setClient(client);
        invoice.setPaymentMethod(PaymentMethod.CRYPTO_ESCROW);
        invoice.setWalletAddress(FREELANCER_WALLET);
        return invoice;
    }

    private EscrowSnapshot createSnapshot(String client, String freelancer, String token, BigInteger amount) {
        return new EscrowSnapshot(client, freelancer, token, amount, EscrowStatus.DEPOSITED);
    }
}
