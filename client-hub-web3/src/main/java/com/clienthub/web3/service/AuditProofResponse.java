package com.clienthub.web3.service;

import com.clienthub.domain.enums.AuditVerificationStatus;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

public record AuditProofResponse(
        Long auditLogId, UUID batchId, String batchStatus, AuditVerificationStatus verificationStatus,
        String hashVersion, String leafHash, int leafIndex, List<String> proof, String merkleRoot,
        String metadataHash, Long chainId, String contractAddress, String transactionHash,
        BigInteger submittedBlock, BigInteger confirmedBlock, int confirmations) {
}
