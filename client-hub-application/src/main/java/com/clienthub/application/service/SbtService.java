package com.clienthub.application.service;

import com.clienthub.application.dto.certificate.CertificateResponse;
import com.clienthub.application.exception.ResourceNotFoundException;
import com.clienthub.common.service.TenantAwareService;
import com.clienthub.domain.entity.Certificate;
import com.clienthub.domain.entity.Invoice;
import com.clienthub.domain.repository.CertificateRepository;
import com.clienthub.domain.repository.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "blockchain.enabled", havingValue = "true")
public class SbtService extends TenantAwareService {
    
    private static final Logger logger = LoggerFactory.getLogger(SbtService.class);

    private final CertificateRepository certificateRepository;
    private final InvoiceRepository invoiceRepository;
    private final SbtMetadataService sbtMetadataService;

    public SbtService(CertificateRepository certificateRepository, 
                      InvoiceRepository invoiceRepository, 
                      SbtMetadataService sbtMetadataService) {
        this.certificateRepository = certificateRepository;
        this.invoiceRepository = invoiceRepository;
        this.sbtMetadataService = sbtMetadataService;
    }

    @Transactional
    public void mintCertificateForInvoice(Long invoiceId) {
        String tenantId = getCurrentTenantId();
        Invoice invoice = invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        mintCertificateForInvoice(invoice);
    }

    private void mintCertificateForInvoice(Invoice invoice) {
        String tenantId = invoice.getTenantId();
        UUID freelancerId = invoice.getFreelancer().getId();
        UUID projectId = invoice.getProject().getId();

        // Check if certificate already exists
        if (certificateRepository.findByProjectIdAndUserIdAndTenantId(projectId, freelancerId, tenantId).isPresent()) {
            logger.info("Certificate already exists for Project {} and Freelancer {}", projectId, freelancerId);
            return;
        }

        // Upload metadata to IPFS
        String metadataUri = sbtMetadataService.generateAndUploadMetadata(
                invoice.getFreelancer().getFullName(),
                invoice.getProject().getTitle(),
                projectId,
                invoice.getClient().getFullName()
        );

        // TODO: In a fully integrated Web3 environment, we would call the WorkCertificate smart contract here
        // using Web3j to actually mint the token and get the token ID and transaction hash.
        // For now, we simulate the minting process.
        String simulatedTokenId = UUID.randomUUID().toString().substring(0, 8);
        String simulatedTxHash = "0x" + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");

        Certificate certificate = Certificate.builder()
                .tenantId(tenantId)
                .user(invoice.getFreelancer())
                .project(invoice.getProject())
                .tokenId(simulatedTokenId)
                .metadataUri(metadataUri)
                .transactionHash(simulatedTxHash)
                .build();

        certificateRepository.save(certificate);
        logger.info("Minted SBT Certificate {} for Freelancer {} on Project {}", simulatedTokenId, freelancerId, projectId);
    }

    @org.springframework.context.event.EventListener
    @Transactional
    public void onInvoiceStatusChanged(com.clienthub.domain.event.InvoiceStatusChangedEvent event) {
        if (event.getInvoice().getStatus() == com.clienthub.domain.enums.InvoiceStatus.PAID) {
            try {
                mintCertificateForInvoice(event.getInvoice());
            } catch (Exception e) {
                logger.error("Failed to mint SBT for invoice " + event.getInvoice().getId(), e);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<CertificateResponse> getUserCertificates(UUID userId) {
        String tenantId = getCurrentTenantId();
        return certificateRepository.findByUserIdAndTenantId(userId, tenantId).stream()
                .map(cert -> CertificateResponse.builder()
                        .id(cert.getId())
                        .userId(cert.getUser().getId())
                        .projectId(cert.getProject().getId())
                        .projectName(cert.getProject().getTitle())
                        .tokenId(cert.getTokenId())
                        .metadataUri(cert.getMetadataUri())
                        .transactionHash(cert.getTransactionHash())
                        .mintedAt(cert.getMintedAt())
                        .build())
                .collect(Collectors.toList());
    }
}
