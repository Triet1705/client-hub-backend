package com.clienthub.application.service;

import com.clienthub.common.context.TenantContext;
import com.clienthub.domain.entity.Invoice;
import com.clienthub.domain.entity.Project;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.InvoiceStatus;
import com.clienthub.domain.enums.Role;
import com.clienthub.application.dto.invoice.InvoiceRequest;
import com.clienthub.application.dto.invoice.InvoiceResponse;
import com.clienthub.application.exception.ResourceNotFoundException;
import com.clienthub.application.mapper.InvoiceMapper;
import com.clienthub.domain.repository.InvoiceRepository;
import com.clienthub.domain.repository.ProjectRepository;
import com.clienthub.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final InvoiceMapper invoiceMapper;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          ProjectRepository projectRepository,
                          UserRepository userRepository,
                          InvoiceMapper invoiceMapper) {
        this.invoiceRepository = invoiceRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.invoiceMapper = invoiceMapper;
    }

    public InvoiceResponse createInvoice(InvoiceRequest request, UUID freelancerId) {
        String tenantId = TenantContext.getTenantId();

        Project project = projectRepository.findById(request.getProjectId())
                .filter(p -> p.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Project not found or access denied"));

        User client = userRepository.findById(request.getClientId())
                .filter(u -> u.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));

        User freelancer = userRepository.findById(freelancerId)
                .filter(u -> u.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Freelancer profile not found"));

        Invoice invoice = invoiceMapper.toEntity(request);

        invoice.setProject(project);
        invoice.setClient(client);
        invoice.setFreelancer(freelancer);

        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setTenantId(tenantId);

        Invoice savedInvoice = invoiceRepository.save(invoice);
        return invoiceMapper.toResponse(savedInvoice);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceById(Long id) {
        String tenantId = TenantContext.getTenantId();
        Invoice invoice = invoiceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        return invoiceMapper.toResponse(invoice);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceByIdWithOwnershipCheck(Long id, UUID currentUserId) {
        String tenantId = TenantContext.getTenantId();
        Invoice invoice = invoiceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        if (currentUser.getRole() == Role.ADMIN) {
            return invoiceMapper.toResponse(invoice);
        }

        UUID clientId = invoice.getClient().getId();
        UUID freelancerId = invoice.getFreelancer().getId();
        
        if (!currentUserId.equals(clientId) && !currentUserId.equals(freelancerId)) {
            throw new ResourceNotFoundException("Invoice not found or access denied");
        }

        return invoiceMapper.toResponse(invoice);
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> getInvoicesByProject(UUID projectId) {
        String tenantId = TenantContext.getTenantId();

        if (!projectRepository.existsByIdAndTenantId(projectId, tenantId)) {
            throw new ResourceNotFoundException("Project not found or access denied");
        }

        return invoiceRepository.findByProjectIdAndTenantId(projectId, tenantId)
                .stream()
                .map(invoiceMapper::toResponse)
                .collect(Collectors.toList());
    }

    public InvoiceResponse updateStatus(Long id, InvoiceStatus newStatus, UUID currentUserId) {
        String tenantId = TenantContext.getTenantId();
        Invoice invoice = invoiceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        UUID clientId = invoice.getClient().getId();
        boolean isClient = currentUserId.equals(clientId);
        
        if (!isClient && !isAdmin) {
            throw new ResourceNotFoundException("You do not have permission to update this invoice");
        }

        if (!invoice.getStatus().canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    String.format("Invalid state transition from %s to %s", invoice.getStatus(), newStatus)
            );
        }

        invoice.setStatus(newStatus);

        if (newStatus == InvoiceStatus.PAID) {
            invoice.setPaidAt(java.time.Instant.now());
        }

        Invoice updated = invoiceRepository.save(invoice);
        return invoiceMapper.toResponse(updated);
    }
}