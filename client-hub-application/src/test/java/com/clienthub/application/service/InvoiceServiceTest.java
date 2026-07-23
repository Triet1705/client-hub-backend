package com.clienthub.application.service;

import com.clienthub.application.mapper.InvoiceMapper;
import com.clienthub.application.dto.invoice.InvoiceResponse;
import com.clienthub.application.exception.ResourceNotFoundException;
import com.clienthub.common.context.TenantContext;
import com.clienthub.domain.entity.Invoice;
import com.clienthub.domain.entity.Project;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.InvoiceStatus;
import com.clienthub.domain.enums.PaymentMethod;
import com.clienthub.domain.enums.Role;
import com.clienthub.domain.repository.InvoiceRepository;
import com.clienthub.domain.repository.ProjectMemberRepository;
import com.clienthub.domain.repository.ProjectRepository;
import com.clienthub.domain.repository.UserRepository;
import com.clienthub.domain.repository.AuditLogRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.List;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {
    private static final String TENANT_ID = "default";
    private static final Long INVOICE_ID = 10L;
    private static final UUID PROJECT_ID = UUID.randomUUID();
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

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditProofReader auditProofReader;

    @Mock
    private ApplicationEventPublisher eventPublisher;

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

    @Test
    void getAuditProof_WhenHistoricalInvoiceHasNoAuditRecord_ReturnsUnavailable() {
        Invoice invoice = createCryptoInvoice();
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID)).thenReturn(Optional.of(invoice));
        when(userRepository.findByIdAndTenantId(CLIENT_ID, TENANT_ID)).thenReturn(Optional.of(invoice.getClient()));
        when(auditLogRepository.findFirstByEntityTypeAndEntityIdAndTenantIdOrderByCreatedAtDescIdDesc(
                "INVOICE", String.valueOf(INVOICE_ID), TENANT_ID)).thenReturn(Optional.empty());

        var proof = invoiceService.getAuditProof(INVOICE_ID, CLIENT_ID);

        assertFalse(proof.proofAvailable());
        assertEquals(com.clienthub.domain.enums.AuditVerificationStatus.NOT_ANCHORED, proof.verificationStatus());
    }

    @Test
    void getInvoiceById_WhenCallerIsCorrectRoleClient_ShouldAllow() {
        Invoice invoice = createProjectInvoice();
        InvoiceResponse expected = new InvoiceResponse();
        stubDirectInvoice(invoice.getClient(), invoice);
        when(invoiceMapper.toResponse(invoice)).thenReturn(expected);

        assertSame(expected, invoiceService.getInvoiceByIdWithOwnershipCheck(
                INVOICE_ID, CLIENT_ID));
    }

    @Test
    void getInvoiceById_WhenCallerIsCorrectRoleFreelancer_ShouldAllow() {
        Invoice invoice = createProjectInvoice();
        InvoiceResponse expected = new InvoiceResponse();
        stubDirectInvoice(invoice.getFreelancer(), invoice);
        when(invoiceMapper.toResponse(invoice)).thenReturn(expected);

        assertSame(expected, invoiceService.getInvoiceByIdWithOwnershipCheck(
                INVOICE_ID, FREELANCER_ID));
    }

    @Test
    void getInvoiceById_WhenCallerIsTenantAdmin_ShouldAllow() {
        UUID adminId = UUID.randomUUID();
        User admin = createUser(adminId, Role.ADMIN, "admin@example.com");
        Invoice invoice = createProjectInvoice();
        InvoiceResponse expected = new InvoiceResponse();
        stubDirectInvoice(admin, invoice);
        when(invoiceMapper.toResponse(invoice)).thenReturn(expected);

        assertSame(expected, invoiceService.getInvoiceByIdWithOwnershipCheck(
                INVOICE_ID, adminId));
    }

    @Test
    void getInvoiceById_WhenClientPartyUuidHasFreelancerRole_ShouldDeny() {
        Invoice invoice = createProjectInvoice();
        User wrongRoleParty = createUser(
                CLIENT_ID, Role.FREELANCER, "wrong-role@example.com");
        stubDirectInvoice(wrongRoleParty, invoice);

        assertThrows(ResourceNotFoundException.class,
                () -> invoiceService.getInvoiceByIdWithOwnershipCheck(
                        INVOICE_ID, CLIENT_ID));
        verify(invoiceMapper, never()).toResponse(any(Invoice.class));
    }

    @Test
    void getInvoiceById_WhenFreelancerPartyUuidHasClientRole_ShouldDeny() {
        Invoice invoice = createProjectInvoice();
        User wrongRoleParty = createUser(
                FREELANCER_ID, Role.CLIENT, "wrong-role@example.com");
        stubDirectInvoice(wrongRoleParty, invoice);

        assertThrows(ResourceNotFoundException.class,
                () -> invoiceService.getInvoiceByIdWithOwnershipCheck(
                        INVOICE_ID, FREELANCER_ID));
    }

    @Test
    void getInvoiceById_WhenCorrectRoleHasWrongUuid_ShouldDeny() {
        UUID outsiderId = UUID.randomUUID();
        Invoice invoice = createProjectInvoice();
        User outsider = createUser(outsiderId, Role.CLIENT, "outsider@example.com");
        stubDirectInvoice(outsider, invoice);

        assertThrows(ResourceNotFoundException.class,
                () -> invoiceService.getInvoiceByIdWithOwnershipCheck(
                        INVOICE_ID, outsiderId));
    }

    @Test
    void getInvoiceById_WhenInvoiceIsCrossTenant_ShouldReturnNonDisclosingNotFound() {
        String otherTenant = "other-tenant";
        TenantContext.setTenantId(otherTenant);
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, otherTenant))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> invoiceService.getInvoiceByIdWithOwnershipCheck(
                        INVOICE_ID, CLIENT_ID));
        verifyNoInteractions(userRepository, invoiceMapper);
    }

    @Test
    void getAllInvoices_WhenClientPartyHasCorrectRole_ShouldAllow() {
        Invoice invoice = createProjectInvoice();
        when(userRepository.findByIdAndTenantId(CLIENT_ID, TENANT_ID))
                .thenReturn(Optional.of(invoice.getClient()));
        when(invoiceRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(invoice));
        InvoiceResponse expected = new InvoiceResponse();
        when(invoiceMapper.toResponse(invoice)).thenReturn(expected);

        assertEquals(List.of(expected), invoiceService.getAllInvoices(
                null, null, CLIENT_ID));
    }

    @Test
    void getAllInvoices_WhenPartyUuidHasWrongRole_ShouldReturnEmpty() {
        Invoice invoice = createProjectInvoice();
        User wrongRoleParty = createUser(
                CLIENT_ID, Role.FREELANCER, "wrong-role@example.com");
        when(userRepository.findByIdAndTenantId(CLIENT_ID, TENANT_ID))
                .thenReturn(Optional.of(wrongRoleParty));
        when(invoiceRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(invoice));

        assertTrue(invoiceService.getAllInvoices(null, null, CLIENT_ID).isEmpty());
        verify(invoiceMapper, never()).toResponse(any(Invoice.class));
    }

    @Test
    void getInvoicesByProject_WhenCallerIsInvoiceClient_ShouldAllow() {
        Invoice invoice = createProjectInvoice();
        InvoiceResponse expected = new InvoiceResponse();
        stubProjectInvoices(CLIENT_ID, invoice.getClient(), invoice);
        when(invoiceMapper.toResponse(invoice)).thenReturn(expected);

        List<InvoiceResponse> result = invoiceService.getInvoicesByProject(PROJECT_ID, CLIENT_ID);

        assertEquals(1, result.size());
        assertSame(expected, result.getFirst());
    }

    @Test
    void getInvoicesByProject_WhenCallerIsInvoiceFreelancer_ShouldAllow() {
        Invoice invoice = createProjectInvoice();
        InvoiceResponse expected = new InvoiceResponse();
        stubProjectInvoices(FREELANCER_ID, invoice.getFreelancer(), invoice);
        when(invoiceMapper.toResponse(invoice)).thenReturn(expected);

        List<InvoiceResponse> result = invoiceService.getInvoicesByProject(PROJECT_ID, FREELANCER_ID);

        assertEquals(1, result.size());
        assertSame(expected, result.getFirst());
    }

    @Test
    void getInvoicesByProject_WhenCallerIsTenantAdmin_ShouldAllow() {
        UUID adminId = UUID.randomUUID();
        User admin = createUser(adminId, Role.ADMIN, "admin@example.com");
        Invoice invoice = createProjectInvoice();
        InvoiceResponse expected = new InvoiceResponse();
        stubProjectInvoices(adminId, admin, invoice);
        when(invoiceMapper.toResponse(invoice)).thenReturn(expected);

        List<InvoiceResponse> result = invoiceService.getInvoicesByProject(PROJECT_ID, adminId);

        assertEquals(1, result.size());
        assertSame(expected, result.getFirst());
    }

    @Test
    void getInvoicesByProject_WhenCallerIsUnrelatedSameTenantClient_ShouldDeny() {
        UUID outsiderId = UUID.randomUUID();
        User outsider = createUser(outsiderId, Role.CLIENT, "outsider-client@example.com");
        Invoice invoice = createProjectInvoice();
        stubProjectInvoices(outsiderId, outsider, invoice);

        assertThrows(ResourceNotFoundException.class,
                () -> invoiceService.getInvoicesByProject(PROJECT_ID, outsiderId));
        verify(invoiceMapper, never()).toResponse(any(Invoice.class));
    }

    @Test
    void getInvoicesByProject_WhenProjectIsCrossTenant_ShouldReturnNonDisclosingNotFound() {
        String otherTenant = "other-tenant";
        TenantContext.setTenantId(otherTenant);
        when(projectRepository.existsByIdAndTenantId(PROJECT_ID, otherTenant)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> invoiceService.getInvoicesByProject(PROJECT_ID, CLIENT_ID));
        verifyNoInteractions(userRepository, invoiceRepository, invoiceMapper);
    }

    @Test
    void getInvoicesByProject_WhenProjectMemberIsNotInvoiceParty_ShouldDenyPrivilegeEscalation() {
        UUID projectMemberId = UUID.randomUUID();
        User projectMember = createUser(projectMemberId, Role.FREELANCER, "member@example.com");
        Invoice invoice = createProjectInvoice();
        stubProjectInvoices(projectMemberId, projectMember, invoice);

        assertThrows(ResourceNotFoundException.class,
                () -> invoiceService.getInvoicesByProject(PROJECT_ID, projectMemberId));
        verifyNoInteractions(projectMemberRepository);
        verify(invoiceMapper, never()).toResponse(any(Invoice.class));
    }

    @Test
    void getInvoicesByProject_WhenClientPartyUuidHasWrongRole_ShouldDeny() {
        Invoice invoice = createProjectInvoice();
        User wrongRoleParty = createUser(
                CLIENT_ID, Role.FREELANCER, "wrong-role@example.com");
        stubProjectInvoices(CLIENT_ID, wrongRoleParty, invoice);

        assertThrows(ResourceNotFoundException.class,
                () -> invoiceService.getInvoicesByProject(PROJECT_ID, CLIENT_ID));
        verify(invoiceMapper, never()).toResponse(any(Invoice.class));
    }

    @Test
    void updateStatus_WhenInvoiceClientHasCorrectRole_ShouldAllow() {
        Invoice invoice = createProjectInvoice();
        InvoiceResponse expected = new InvoiceResponse();
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID))
                .thenReturn(Optional.of(invoice));
        when(userRepository.findByIdAndTenantId(CLIENT_ID, TENANT_ID))
                .thenReturn(Optional.of(invoice.getClient()));
        when(invoiceRepository.save(invoice)).thenReturn(invoice);
        when(invoiceMapper.toResponse(invoice)).thenReturn(expected);

        assertSame(expected, invoiceService.updateStatus(
                INVOICE_ID, InvoiceStatus.SENT, CLIENT_ID));
        verify(invoiceRepository).save(invoice);
    }

    @Test
    void updateStatus_WhenCallerIsTenantAdmin_ShouldAllow() {
        UUID adminId = UUID.randomUUID();
        User admin = createUser(adminId, Role.ADMIN, "admin@example.com");
        Invoice invoice = createProjectInvoice();
        InvoiceResponse expected = new InvoiceResponse();
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID))
                .thenReturn(Optional.of(invoice));
        when(userRepository.findByIdAndTenantId(adminId, TENANT_ID))
                .thenReturn(Optional.of(admin));
        when(invoiceRepository.save(invoice)).thenReturn(invoice);
        when(invoiceMapper.toResponse(invoice)).thenReturn(expected);

        assertSame(expected, invoiceService.updateStatus(
                INVOICE_ID, InvoiceStatus.SENT, adminId));
    }

    @Test
    void updateStatus_WhenClientPartyUuidHasWrongRole_ShouldDeny() {
        Invoice invoice = createProjectInvoice();
        User wrongRoleParty = createUser(
                CLIENT_ID, Role.FREELANCER, "wrong-role@example.com");
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID))
                .thenReturn(Optional.of(invoice));
        when(userRepository.findByIdAndTenantId(CLIENT_ID, TENANT_ID))
                .thenReturn(Optional.of(wrongRoleParty));

        assertThrows(ResourceNotFoundException.class,
                () -> invoiceService.updateStatus(
                        INVOICE_ID, InvoiceStatus.SENT, CLIENT_ID));
        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    void updateStatus_WhenCorrectRoleHasWrongUuid_ShouldDeny() {
        UUID outsiderId = UUID.randomUUID();
        User outsider = createUser(outsiderId, Role.CLIENT, "outsider@example.com");
        Invoice invoice = createProjectInvoice();
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID))
                .thenReturn(Optional.of(invoice));
        when(userRepository.findByIdAndTenantId(outsiderId, TENANT_ID))
                .thenReturn(Optional.of(outsider));

        assertThrows(ResourceNotFoundException.class,
                () -> invoiceService.updateStatus(
                        INVOICE_ID, InvoiceStatus.SENT, outsiderId));
        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    void verifyAuditProof_WhenInvoiceFreelancerHasCorrectRole_ShouldAllow() {
        Invoice invoice = createProjectInvoice();
        stubDirectInvoice(invoice.getFreelancer(), invoice);
        when(auditLogRepository.findFirstByEntityTypeAndEntityIdAndTenantIdOrderByCreatedAtDescIdDesc(
                "INVOICE", String.valueOf(INVOICE_ID), TENANT_ID))
                .thenReturn(Optional.empty());

        assertFalse(invoiceService.verifyAuditProof(
                INVOICE_ID, FREELANCER_ID).proofAvailable());
    }

    @Test
    void getAuditProof_WhenPartyUuidHasWrongRole_ShouldDeny() {
        Invoice invoice = createProjectInvoice();
        User wrongRoleParty = createUser(
                CLIENT_ID, Role.FREELANCER, "wrong-role@example.com");
        stubDirectInvoice(wrongRoleParty, invoice);

        assertThrows(ResourceNotFoundException.class,
                () -> invoiceService.getAuditProof(INVOICE_ID, CLIENT_ID));
        verifyNoInteractions(auditLogRepository, auditProofReader);
    }

    private Invoice createCryptoInvoice() {
        User client = createUser(CLIENT_ID, Role.CLIENT, "client@example.com");
        client.setWalletAddress("0x70997970C51812dc3A010C7d01b50e0d17dc79C8");
        User freelancer = createUser(FREELANCER_ID, Role.FREELANCER, "freelancer@example.com");
        freelancer.setWalletAddress("0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC");

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

    private Invoice createProjectInvoice() {
        Invoice invoice = createCryptoInvoice();
        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setTenantId(TENANT_ID);
        project.setOwner(invoice.getClient());
        invoice.setProject(project);
        invoice.setPaymentMethod(PaymentMethod.FIAT);
        invoice.setStatus(InvoiceStatus.DRAFT);
        return invoice;
    }

    private User createUser(UUID id, Role role, String email) {
        return User.builder()
                .id(id)
                .tenantId(TENANT_ID)
                .email(email)
                .password("password")
                .fullName(email)
                .role(role)
                .build();
    }

    private void stubProjectInvoices(UUID currentUserId, User currentUser, Invoice invoice) {
        when(projectRepository.existsByIdAndTenantId(PROJECT_ID, TENANT_ID)).thenReturn(true);
        when(userRepository.findByIdAndTenantId(currentUserId, TENANT_ID)).thenReturn(Optional.of(currentUser));
        when(invoiceRepository.findByProjectIdAndTenantId(PROJECT_ID, TENANT_ID)).thenReturn(List.of(invoice));
    }

    private void stubDirectInvoice(User currentUser, Invoice invoice) {
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID))
                .thenReturn(Optional.of(invoice));
        when(userRepository.findByIdAndTenantId(currentUser.getId(), TENANT_ID))
                .thenReturn(Optional.of(currentUser));
    }
}
