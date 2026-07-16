package com.clienthub.web.controller;

import com.clienthub.domain.enums.InvoiceStatus;
import com.clienthub.application.dto.invoice.InvoiceRequest;
import com.clienthub.application.dto.invoice.InvoiceResponse;
import com.clienthub.application.dto.audit.UserAuditProofResponse;
import com.clienthub.infrastructure.security.CustomUserDetails;
import com.clienthub.application.service.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<InvoiceResponse> createInvoice(
            @Valid @RequestBody InvoiceRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        InvoiceResponse response = invoiceService.createInvoice(request, currentUser.getId());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    public ResponseEntity<InvoiceResponse> getInvoiceById(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(invoiceService.getInvoiceByIdWithOwnershipCheck(id, currentUser.getId()));
    }

    @GetMapping("/project/{projectId}")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    public ResponseEntity<List<InvoiceResponse>> getInvoicesByProject(@PathVariable UUID projectId) {
        return ResponseEntity.ok(invoiceService.getInvoicesByProject(projectId));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')") // Client confirm payment
    public ResponseEntity<InvoiceResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam InvoiceStatus status,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(invoiceService.updateStatus(id, status, currentUser.getId()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    public ResponseEntity<List<InvoiceResponse>> getAllInvoices(
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) UUID projectId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(invoiceService.getAllInvoices(status, projectId, currentUser.getId()));
    }

    @GetMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    public ResponseEntity<?> getInvoiceStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        // Just reuse the standard getInvoiceById logic but return a smaller payload for polling
        InvoiceResponse response = invoiceService.getInvoiceByIdWithOwnershipCheck(id, currentUser.getId());
        return ResponseEntity.ok(java.util.Map.of(
                "id", response.getId(),
                "status", response.getStatus(),
                "escrowStatus", response.getEscrowStatus() != null ? response.getEscrowStatus() : "NOT_STARTED"
        ));
    }

    @GetMapping("/{id}/audit-proof")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    public ResponseEntity<UserAuditProofResponse> getInvoiceAuditProof(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(invoiceService.getAuditProof(id, currentUser.getId()));
    }

    @PostMapping("/{id}/audit-proof/verify")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    public ResponseEntity<UserAuditProofResponse> verifyInvoiceAuditProof(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(invoiceService.verifyAuditProof(id, currentUser.getId()));
    }
}
