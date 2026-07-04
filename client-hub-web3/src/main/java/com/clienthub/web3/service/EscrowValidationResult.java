package com.clienthub.web3.service;

public record EscrowValidationResult(boolean valid, String reason) {
    public static EscrowValidationResult success() {
        return new EscrowValidationResult(true, "");
    }

    public static EscrowValidationResult invalid(String reason) {
        return new EscrowValidationResult(false, reason);
    }
}
