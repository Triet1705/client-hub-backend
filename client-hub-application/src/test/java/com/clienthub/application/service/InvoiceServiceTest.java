package com.clienthub.application.service;

import com.clienthub.application.mapper.InvoiceMapper;
import com.clienthub.common.context.TenantContext;
import com.clienthub.domain.entity.Invoice;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.InvoiceStatus;
import com.clienthub.domain.enums.PaymentMethod;
import com.clienthub.domain.enums.Role;
import com.clienthub.domain.repository.InvoiceRepository;
import com.clienthub.domain.repository.ProjectMemberRepository;
import com.clienthub.domain.repository.ProjectRepository;
import com.clienthub.domain.repository.UserRepository;
import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {
    private static final String TENANT_ID = "default";
    private static final Long INVOICE_ID = 10L;
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID FREELANCER_ID = UUID.randomUUID();

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InvoiceMapper invoiceMapper;

    @Mock
    private NotificationProducerService notificationProducerService;

    @InjectMocks
    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @ParameterizedTest
    @EnumSource(value = InvoiceStatus.class, names = {"DEPOSIT_DETECTED", "LOCKED", "PAID", "REFUNDED"})
    void updateStatus_WhenCryptoEscrowTargetsChainDerivedStatus_ShouldReject(InvoiceStatus targetStatus) {
        Invoice invoice = createCryptoInvoice();
        User client = invoice.getClient();

        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID)).thenReturn(Optional.of(invoice));
        when(userRepository.findByIdAndTenantId(CLIENT_ID, TENANT_ID)).thenReturn(Optional.of(client));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> invoiceService.updateStatus(INVOICE_ID, targetStatus, CLIENT_ID));

        assertEquals("Crypto escrow invoice status is managed by blockchain events", exception.getMessage());
        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    private Invoice createCryptoInvoice() {
        User client = User.builder()
                .id(CLIENT_ID)
                .tenantId(TENANT_ID)
                .email("client@example.com")
                .password("password")
                .fullName("Client")
                .role(Role.CLIENT)
                .walletAddress("0x70997970C51812dc3A010C7d01b50e0d17dc79C8")
                .build();
        User freelancer = User.builder()
                .id(FREELANCER_ID)
                .tenantId(TENANT_ID)
                .email("freelancer@example.com")
                .password("password")
                .fullName("Freelancer")
                .role(Role.FREELANCER)
                .walletAddress("0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC")
                .build();

        Invoice invoice = new Invoice();
        invoice.setId(INVOICE_ID);
        invoice.setTenantId(TENANT_ID);
        invoice.setTitle("Crypto invoice");
        invoice.setAmount(BigInteger.valueOf(25));
        invoice.setClient(client);
        invoice.setFreelancer(freelancer);
        invoice.setPaymentMethod(PaymentMethod.CRYPTO_ESCROW);
        invoice.setStatus(InvoiceStatus.CRYPTO_ESCROW_WAITING);
        invoice.setWalletAddress(freelancer.getWalletAddress());
        return invoice;
    }
}
