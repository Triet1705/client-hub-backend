package com.clienthub.web.controller;

import com.clienthub.application.dto.certificate.CertificateResponse;
import com.clienthub.infrastructure.security.CustomUserDetails;
import com.clienthub.application.service.SbtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/certificates")
@Tag(name = "Certificates", description = "Endpoints for Soulbound Token Certificates")
public class SbtController {

    private final SbtService sbtService;

    @Autowired
    public SbtController(@Autowired(required = false) SbtService sbtService) {
        this.sbtService = sbtService;
    }

    @Operation(summary = "Get user certificates", description = "Retrieves SBT certificates for a specific user")
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    public ResponseEntity<List<CertificateResponse>> getUserCertificates(
            @PathVariable UUID userId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
            
        if (!currentUser.getId().equals(userId) 
            && currentUser.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
            
        if (sbtService == null) {
            // Blockchain feature is disabled
            return ResponseEntity.ok(List.of());
        }
        
        return ResponseEntity.ok(sbtService.getUserCertificates(userId));
    }
}
