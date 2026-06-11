package com.clienthub.web.controller;

import com.clienthub.application.dto.WalletBindingRequest;
import com.clienthub.application.service.UserService;
import com.clienthub.infrastructure.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PutMapping("/me/wallet")
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER', 'ADMIN')")
    public ResponseEntity<Void> bindWallet(
            @Valid @RequestBody WalletBindingRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        
        userService.updateWalletAddress(currentUser.getId(), request.getWalletAddress());
        return ResponseEntity.noContent().build();
    }
}
