package com.clienthub.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "audit_anchor_members")
public class AuditAnchorMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private AuditAnchorBatch batch;

    @Column(name = "audit_log_id", nullable = false, unique = true)
    private Long auditLogId;

    @Column(name = "leaf_index", nullable = false)
    private int leafIndex;

    @Column(name = "leaf_hash", nullable = false, length = 66)
    private String leafHash;

    @Column(name = "merkle_proof", nullable = false, columnDefinition = "text")
    private String merkleProof;

    public AuditAnchorMember() {}

    public AuditAnchorMember(AuditAnchorBatch batch, Long auditLogId, int leafIndex,
                             String leafHash, String merkleProof) {
        this.batch = batch;
        this.auditLogId = auditLogId;
        this.leafIndex = leafIndex;
        this.leafHash = leafHash;
        this.merkleProof = merkleProof;
    }

    public Long getId() { return id; }
    public AuditAnchorBatch getBatch() { return batch; }
    public Long getAuditLogId() { return auditLogId; }
    public int getLeafIndex() { return leafIndex; }
    public String getLeafHash() { return leafHash; }
    public String getMerkleProof() { return merkleProof; }
}
