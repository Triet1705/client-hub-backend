package com.clienthub.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class WalletBindingRequest {

    @NotBlank(message = "Wallet address cannot be blank")
    @Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "Invalid Ethereum wallet address format")
    private String walletAddress;

    public WalletBindingRequest() {
    }

    public WalletBindingRequest(String walletAddress) {
        this.walletAddress = walletAddress;
    }

    public String getWalletAddress() {
        return walletAddress;
    }

    public void setWalletAddress(String walletAddress) {
        this.walletAddress = walletAddress;
    }
}
