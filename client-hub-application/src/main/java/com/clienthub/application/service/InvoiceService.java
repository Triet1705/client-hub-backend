package com.clienthub.application.service;

import com.clienthub.common.service.TenantAwareService;
import com.clienthub.domain.entity.Invoice;
import com.clienthub.domain.entity.Project;
import com.clienthub.domain.entity.ProjectMember;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.InvoiceStatus;
import com.clienthub.domain.enums.PaymentMethod;
import com.clienthub.domain.enums.Role;
import com.clienthub.application.dto.invoice.InvoiceRequest;
import com.clienthub.application.dto.invoice.InvoiceResponse;
import com.clienthub.application.dto.audit.UserAuditProofResponse;
import com.clienthub.application.exception.ResourceNotFoundException;
import com.clienthub.application.exception.InvalidInvoiceStateException;
import com.clienthub.application.mapper.InvoiceMapper;
import com.clienthub.domain.repository.InvoiceRepository;
import com.clienthub.domain.repository.AuditLogRepository;
import com.clienthub.domain.event.InvoiceStatusChangedEvent;
import com.clienthub.domain.repository.ProjectMemberRepository;
import com.clienthub.domain.repository.ProjectRepository;
import com.clienthub.domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class InvoiceService extends TenantAwareService {
    private static final Set<InvoiceStatus> CHAIN_DERIVED_CRYPTO_STATUSES = Set.of(
            InvoiceStatus.DEPOSIT_DETECTED,
            InvoiceStatus.LOCKED,
            InvoiceStatus.PAID,
            InvoiceStatus.REFUNDED
    );

    private final InvoiceRepository invoiceRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final InvoiceMapper invoiceMapper;
    private final NotificationProducerService notificationProducerService;
    private final AuditLogRepository auditLogRepository;
    private final AuditProofReader auditProofReader;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${blockchain.enabled:false}")
    private boolean blockchainEnabled;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          ProjectRepository projectRepository,
                          ProjectMemberRepository projectMemberRepository,
                          UserRepository userRepository,
                          InvoiceMapper invoiceMapper,
                          NotificationProducerService notificationProducerService,
                          AuditLogRepository auditLogRepository,
                          AuditProofReader auditProofReader,
                          ApplicationEventPublisher eventPublisher) {
        this.invoiceRepository = invoiceRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.userRepository = userRepository;
        this.invoiceMapper = invoiceMapper;
        this.notificationProducerService = notificationProducerService;
        this.auditLogRepository = auditLogRepository;
        this.auditProofReader = auditProofReader;
        this.eventPublisher = eventPublisher;
    }

    public InvoiceResponse createInvoice(InvoiceRequest request, UUID currentUserId) {
        String tenantId = getCurrentTenantId();

        Project project = projectRepository.findByIdAndTenantId(request.getProjectId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found or access denied"));

        User currentUser = userRepository.findByIdAndTenantId(currentUserId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (currentUser.getRole() == Role.FREELANCER) {
            throw new AccessDeniedException("Freelancers cannot create invoices");
        }

        PaymentMethod paymentMethod = request.getPaymentMethod() != null ? request.getPaymentMethod() : PaymentMethod.FIAT;
        if (paymentMethod == PaymentMethod.CRYPTO_ESCROW && !blockchainEnabled) {
            throw new IllegalArgumentException("Crypto escrow invoices are disabled");
        }
        if (paymentMethod == PaymentMethod.CRYPTO_ESCROW && !isValidWalletAddress(request.getFreelancerWalletAddress())) {
            throw new IllegalArgumentException("A valid freelancer wallet address is required for crypto escrow invoices");
        }

        User client = resolveInvoiceClient(request, project, currentUser, tenantId);
        User freelancer = resolveProjectFreelancer(project, tenantId);

        Invoice invoice = invoiceMapper.toEntity(request);

        invoice.setProject(project);
        invoice.setClient(client);
        invoice.setFreelancer(freelancer);

        invoice.setStatus(paymentMethod == PaymentMethod.CRYPTO_ESCROW
                ? InvoiceStatus.CRYPTO_ESCROW_WAITING
                : InvoiceStatus.DRAFT);
        invoice.setTenantId(tenantId);

        Invoice savedInvoice = invoiceRepository.save(invoice);
        eventPublisher.publishEvent(new InvoiceStatusChangedEvent(this, savedInvoice, null));
        return invoiceMapper.toResponse(savedInvoice);
    }

    private User resolveInvoiceClient(InvoiceRequest request, Project project, User currentUser, String tenantId) {
        if (currentUser.getRole() == Role.ADMIN) {
            if (request.getClientId() != null) {
                User requestedClient = userRepository.findByIdAndTenantId(request.getClientId(), tenantId)
                        .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
                if (requestedClient.getRole() != Role.CLIENT) {
                    throw new IllegalArgumentException("Invoice client must have CLIENT role");
                }
                if (!project.getOwner().getId().equals(requestedClient.getId())) {
                    throw new AccessDeniedException("Invoice client must be the project owner");
                }
                return requestedClient;
            }
            return project.getOwner();
        }

        if (!project.getOwner().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Clients can only create invoices for their own projects");
        }
        return currentUser;
    }

    private User resolveProjectFreelancer(Project project, String tenantId) {
        return projectMemberRepository.findByIdProjectIdAndTenantId(project.getId(), tenantId).stream()
                .map(ProjectMember::getUser)
                .filter(user -> user.getRole() == Role.FREELANCER)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Project must have a freelancer member before creating an invoice"));
    }

    private boolean isValidWalletAddress(String walletAddress) {
        return walletAddress != null && walletAddress.matches("^0x[a-fA-F0-9]{40}$");
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceByIdWithOwnershipCheck(Long id, UUID currentUserId) {
        return invoiceMapper.toResponse(findAuthorizedInvoice(id, currentUserId));
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> getInvoicesByProject(UUID projectId, UUID currentUserId) {
        String tenantId = getCurrentTenantId();

        if (!projectRepository.existsByIdAndTenantId(projectId, tenantId)) {
            throw new ResourceNotFoundException("Project not found or access denied");
        }

        User currentUser = userRepository.findByIdAndTenantId(currentUserId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<InvoiceResponse> authorizedInvoices = invoiceRepository.findByProjectIdAndTenantId(projectId, tenantId)
                .stream()
                .filter(invoice -> canReadInvoice(invoice, currentUser))
                .map(invoiceMapper::toResponse)
                .collect(Collectors.toList());

        if (currentUser.getRole() != Role.ADMIN && authorizedInvoices.isEmpty()) {
            throw new ResourceNotFoundException("Project invoices not found or access denied");
        }

        return authorizedInvoices;
    }

    public InvoiceResponse updateStatus(Long id, InvoiceStatus newStatus, UUID currentUserId) {
        String tenantId = getCurrentTenantId();
        Invoice invoice = invoiceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        InvoiceStatus oldStatus = invoice.getStatus();

        User currentUser = userRepository.findByIdAndTenantId(currentUserId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        boolean isInvoiceClient = currentUser.getRole() == Role.CLIENT
                && invoice.getClient() != null
                && currentUserId.equals(invoice.getClient().getId());
        
        if (!isInvoiceClient && !isAdmin) {
            throw new ResourceNotFoundException("You do not have permission to update this invoice");
        }

        if (invoice.getPaymentMethod() == PaymentMethod.CRYPTO_ESCROW
                && CHAIN_DERIVED_CRYPTO_STATUSES.contains(newStatus)) {
            throw new InvalidInvoiceStateException(
                    "Crypto escrow invoice status is managed by blockchain events");
        }

        if (!invoice.getStatus().canTransitionTo(newStatus)) {
            throw new InvalidInvoiceStateException(
                    String.format("Invalid state transition from %s to %s", invoice.getStatus(), newStatus)
            );
        }

        invoice.setStatus(newStatus);

        if (newStatus == InvoiceStatus.PAID) {
            invoice.setPaidAt(java.time.Instant.now());
        }

        Invoice updated = invoiceRepository.save(invoice);
        eventPublisher.publishEvent(new InvoiceStatusChangedEvent(this, updated, oldStatus));

        if (oldStatus != InvoiceStatus.PAID && newStatus == InvoiceStatus.PAID) {
            notificationProducerService.notifyInvoicePaid(updated);
        }

        return invoiceMapper.toResponse(updated);
    }

    @Transactional(readOnly = true)
    public UserAuditProofResponse getAuditProof(Long id, UUID currentUserId) {
        Invoice invoice = findAuthorizedInvoice(id, currentUserId);
        return latestInvoiceAuditId(invoice)
                .map(auditProofReader::getUserProof)
                .orElseGet(UserAuditProofResponse::notAvailable);
    }

    @Transactional(readOnly = true)
    public UserAuditProofResponse verifyAuditProof(Long id, UUID currentUserId) {
        Invoice invoice = findAuthorizedInvoice(id, currentUserId);
        return latestInvoiceAuditId(invoice)
                .map(auditProofReader::verifyUserProof)
                .orElseGet(UserAuditProofResponse::notAvailable);
    }

    private java.util.Optional<Long> latestInvoiceAuditId(Invoice invoice) {
        return auditLogRepository.findFirstByEntityTypeAndEntityIdAndTenantIdOrderByCreatedAtDescIdDesc(
                        "INVOICE", String.valueOf(invoice.getId()), invoice.getTenantId())
                .map(com.clienthub.domain.entity.AuditLog::getId);
    }

    private Invoice findAuthorizedInvoice(Long id, UUID currentUserId) {
        String tenantId = getCurrentTenantId();
        Invoice invoice = invoiceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        User currentUser = userRepository.findByIdAndTenantId(currentUserId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!canReadInvoice(invoice, currentUser)) {
            throw new ResourceNotFoundException("Invoice not found or access denied");
        }
        return invoice;
    }

    private boolean canReadInvoice(Invoice invoice, User currentUser) {
        Role role = currentUser.getRole();
        UUID currentUserId = currentUser.getId();
        return role == Role.ADMIN
                || (role == Role.CLIENT
                    && invoice.getClient() != null
                    && currentUserId.equals(invoice.getClient().getId()))
                || (role == Role.FREELANCER
                    && invoice.getFreelancer() != null
                    && currentUserId.equals(invoice.getFreelancer().getId()));
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> getAllInvoices(InvoiceStatus status, UUID projectId, UUID currentUserId) {
        String tenantId = getCurrentTenantId();

        User currentUser = userRepository.findByIdAndTenantId(currentUserId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<Invoice> invoices;
        if (projectId != null && status != null) {
            invoices = invoiceRepository.findByProjectIdAndTenantId(projectId, tenantId)
                    .stream()
                    .filter(i -> i.getStatus() == status)
                    .collect(Collectors.toList());
        } else if (projectId != null) {
            if (!projectRepository.existsByIdAndTenantId(projectId, tenantId)) {
                throw new ResourceNotFoundException("Project not found or access denied");
            }
            invoices = invoiceRepository.findByProjectIdAndTenantId(projectId, tenantId);
        } else if (status != null) {
            invoices = invoiceRepository.findByTenantIdAndStatus(tenantId, status);
        } else {
            invoices = invoiceRepository.findByTenantId(tenantId);
        }

        return invoices.stream()
                .filter(invoice -> canReadInvoice(invoice, currentUser))
                .map(invoiceMapper::toResponse)
                .collect(Collectors.toList());
    }
}
