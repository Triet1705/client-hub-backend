package com.clienthub.web3.service;

import com.clienthub.domain.entity.Invoice;
import com.clienthub.domain.enums.EscrowStatus;
import java.math.BigInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EscrowValidationService {
    @Value("${blockchain.token_address:}")
    private String tokenAddress;

    @Value("${blockchain.token_decimals:18}")
    private int tokenDecimals;

    public EscrowValidationResult validate(Invoice invoice, EscrowSnapshot snapshot, EscrowStatus expectedStatus) {
        if (invoice == null) {
            return EscrowValidationResult.invalid("invoice is missing");
        }
        if (snapshot == null) {
            return EscrowValidationResult.invalid("escrow snapshot is missing");
        }
        if (snapshot.status() != expectedStatus) {
            return EscrowValidationResult.invalid(
                    "chain status " + snapshot.status() + " does not match expected " + expectedStatus);
        }
        if (tokenAddress == null || tokenAddress.isBlank()) {
            return EscrowValidationResult.invalid("configured escrow token address is missing");
        }
        if (!addressesEqual(tokenAddress, snapshot.tokenAddress())) {
            return EscrowValidationResult.invalid("escrow token does not match configured stablecoin");
        }
        if (tokenDecimals < 0) {
            return EscrowValidationResult.invalid("configured escrow token decimals is invalid");
        }
        if (invoice.getAmount() == null) {
            return EscrowValidationResult.invalid("invoice amount is missing");
        }

        BigInteger expectedAmount = invoice.getAmount().multiply(BigInteger.TEN.pow(tokenDecimals));
        if (!expectedAmount.equals(snapshot.amount())) {
            return EscrowValidationResult.invalid("escrow amount does not match invoice amount");
        }
        if (!addressesEqual(invoice.getWalletAddress(), snapshot.freelancerAddress())) {
            return EscrowValidationResult.invalid("escrow freelancer does not match invoice wallet");
        }
        String clientWalletAddress = invoice.getClient() != null ? invoice.getClient().getWalletAddress() : null;
        if (!addressesEqual(clientWalletAddress, snapshot.clientAddress())) {
            return EscrowValidationResult.invalid("escrow client does not match invoice client wallet");
        }

        return EscrowValidationResult.success();
    }

    private boolean addressesEqual(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
