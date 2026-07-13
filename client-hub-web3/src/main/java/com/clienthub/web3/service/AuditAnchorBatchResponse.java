package com.clienthub.web3.service;

import com.clienthub.domain.entity.AuditAnchorBatch;
import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

public record AuditAnchorBatchResponse(
        UUID id, String status, String merkleRoot, String metadataHash, String hashVersion,
        Long chainId, String contractAddress, Long firstLogId, Long lastLogId, int recordCount,
        String transactionHash, BigInteger submittedBlock, BigInteger confirmedBlock,
        int confirmations, int attempts, String lastError, Instant nextAttemptAt,
        Instant createdAt, Instant submittedAt, Instant confirmedAt) {
    public static AuditAnchorBatchResponse from(AuditAnchorBatch batch) {
        return new AuditAnchorBatchResponse(batch.getId(), batch.getStatus().name(), batch.getMerkleRoot(),
                batch.getMetadataHash(), batch.getHashVersion(), batch.getChainId(), batch.getContractAddress(),
                batch.getFirstLogId(), batch.getLastLogId(), batch.getRecordCount(), batch.getTransactionHash(),
                batch.getSubmittedBlock(), batch.getConfirmedBlock(), batch.getConfirmations(), batch.getAttempts(),
                batch.getLastError(), batch.getNextAttemptAt(), batch.getCreatedAt(), batch.getSubmittedAt(), batch.getConfirmedAt());
    }
}
