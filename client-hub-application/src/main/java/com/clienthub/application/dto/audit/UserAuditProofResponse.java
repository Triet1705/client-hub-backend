package com.clienthub.application.dto.audit;

import com.clienthub.domain.enums.AuditVerificationStatus;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

public record UserAuditProofResponse(
        Long auditLogId,
        boolean proofAvailable,
        AuditVerificationStatus verificationStatus,
        String batchStatus,
        Instant anchoredAt,
        int confirmations,
        Long chainId,
        String transactionHash,
        String contractAddress,
        String hashVersion,
        String leafHash,
        int leafIndex,
        List<String> proof,
        String merkleRoot,
        BigInteger confirmedBlock) {

    public static UserAuditProofResponse notAvailable() {
        return notAvailable(null);
    }

    public static UserAuditProofResponse notAvailable(Long auditLogId) {
        return new UserAuditProofResponse(auditLogId, false, AuditVerificationStatus.NOT_ANCHORED,
                null, null, 0, null, null, null, null, null, 0, List.of(), null, null);
    }
}
