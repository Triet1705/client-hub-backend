package com.clienthub.domain.entity;

import com.clienthub.domain.enums.AuditAnchorBatchStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_anchor_batches")
public class AuditAnchorBatch {
    @Id
    @JdbcTypeCode(SqlTypes.OTHER)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditAnchorBatchStatus status;

    @Column(name = "merkle_root", nullable = false, unique = true, length = 66)
    private String merkleRoot;

    @Column(name = "metadata_hash", nullable = false, length = 66)
    private String metadataHash;

    @Column(name = "hash_version", nullable = false, length = 50)
    private String hashVersion;

    @Column(name = "chain_id")
    private Long chainId;

    @Column(name = "contract_address", length = 42)
    private String contractAddress;

    @Column(name = "first_log_id", nullable = false)
    private Long firstLogId;

    @Column(name = "last_log_id", nullable = false)
    private Long lastLogId;

    @Column(name = "record_count", nullable = false)
    private int recordCount;

    @Column(name = "transaction_hash", length = 66)
    private String transactionHash;

    @Column(name = "submitted_block", precision = 78)
    private BigInteger submittedBlock;

    @Column(name = "confirmed_block", precision = 78)
    private BigInteger confirmedBlock;

    @Column(nullable = false)
    private int confirmations;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    public AuditAnchorBatch() {}

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = AuditAnchorBatchStatus.BUILDING;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public AuditAnchorBatchStatus getStatus() { return status; }
    public void setStatus(AuditAnchorBatchStatus status) { this.status = status; }
    public String getMerkleRoot() { return merkleRoot; }
    public void setMerkleRoot(String merkleRoot) { this.merkleRoot = merkleRoot; }
    public String getMetadataHash() { return metadataHash; }
    public void setMetadataHash(String metadataHash) { this.metadataHash = metadataHash; }
    public String getHashVersion() { return hashVersion; }
    public void setHashVersion(String hashVersion) { this.hashVersion = hashVersion; }
    public Long getChainId() { return chainId; }
    public void setChainId(Long chainId) { this.chainId = chainId; }
    public String getContractAddress() { return contractAddress; }
    public void setContractAddress(String contractAddress) { this.contractAddress = contractAddress; }
    public Long getFirstLogId() { return firstLogId; }
    public void setFirstLogId(Long firstLogId) { this.firstLogId = firstLogId; }
    public Long getLastLogId() { return lastLogId; }
    public void setLastLogId(Long lastLogId) { this.lastLogId = lastLogId; }
    public int getRecordCount() { return recordCount; }
    public void setRecordCount(int recordCount) { this.recordCount = recordCount; }
    public String getTransactionHash() { return transactionHash; }
    public void setTransactionHash(String transactionHash) { this.transactionHash = transactionHash; }
    public BigInteger getSubmittedBlock() { return submittedBlock; }
    public void setSubmittedBlock(BigInteger submittedBlock) { this.submittedBlock = submittedBlock; }
    public BigInteger getConfirmedBlock() { return confirmedBlock; }
    public void setConfirmedBlock(BigInteger confirmedBlock) { this.confirmedBlock = confirmedBlock; }
    public int getConfirmations() { return confirmations; }
    public void setConfirmations(int confirmations) { this.confirmations = confirmations; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
}
