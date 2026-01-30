package com.clienthub.web.controller;

import com.clienthub.domain.enums.InvoiceStatus;
import com.clienthub.application.dto.invoice.InvoiceRequest;
import com.clienthub.application.dto.invoice.InvoiceResponse;
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
    @PreAuthorize("hasAnyRole('FREELANCER', 'ADMIN')")
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
}
