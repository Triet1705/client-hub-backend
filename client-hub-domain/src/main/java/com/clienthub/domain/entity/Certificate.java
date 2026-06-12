package com.clienthub.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "certificates")
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "token_id", nullable = false)
    private String tokenId;

    @Column(name = "metadata_uri", nullable = false, length = 1000)
    private String metadataUri;

    @Column(name = "transaction_hash", nullable = false)
    private String transactionHash;

    @Column(name = "minted_at", nullable = false, updatable = false)
    private Instant mintedAt;

    public Certificate() {}

    public Certificate(Long id, String tenantId, User user, Project project, String tokenId, String metadataUri, String transactionHash, Instant mintedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.user = user;
        this.project = project;
        this.tokenId = tokenId;
        this.metadataUri = metadataUri;
        this.transactionHash = transactionHash;
        this.mintedAt = mintedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    public String getTokenId() { return tokenId; }
    public void setTokenId(String tokenId) { this.tokenId = tokenId; }

    public String getMetadataUri() { return metadataUri; }
    public void setMetadataUri(String metadataUri) { this.metadataUri = metadataUri; }

    public String getTransactionHash() { return transactionHash; }
    public void setTransactionHash(String transactionHash) { this.transactionHash = transactionHash; }

    public Instant getMintedAt() { return mintedAt; }
    public void setMintedAt(Instant mintedAt) { this.mintedAt = mintedAt; }

    @PrePersist
    public void prePersist() {
        if (mintedAt == null) {
            mintedAt = Instant.now();
        }
    }

    public static CertificateBuilder builder() {
        return new CertificateBuilder();
    }

    public static class CertificateBuilder {
        private String tenantId;
        private User user;
        private Project project;
        private String tokenId;
        private String metadataUri;
        private String transactionHash;
        private Instant mintedAt;

        public CertificateBuilder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public CertificateBuilder user(User user) { this.user = user; return this; }
        public CertificateBuilder project(Project project) { this.project = project; return this; }
        public CertificateBuilder tokenId(String tokenId) { this.tokenId = tokenId; return this; }
        public CertificateBuilder metadataUri(String metadataUri) { this.metadataUri = metadataUri; return this; }
        public CertificateBuilder transactionHash(String transactionHash) { this.transactionHash = transactionHash; return this; }
        public CertificateBuilder mintedAt(Instant mintedAt) { this.mintedAt = mintedAt; return this; }

        public Certificate build() {
            Certificate cert = new Certificate();
            cert.setTenantId(tenantId);
            cert.setUser(user);
            cert.setProject(project);
            cert.setTokenId(tokenId);
            cert.setMetadataUri(metadataUri);
            cert.setTransactionHash(transactionHash);
            cert.setMintedAt(mintedAt);
            return cert;
        }
    }
}
