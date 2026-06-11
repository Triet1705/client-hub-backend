package com.clienthub.application.dto.certificate;

import java.time.Instant;
import java.util.UUID;

public class CertificateResponse {
    private Long id;
    private UUID userId;
    private UUID projectId;
    private String projectName;
    private String tokenId;
    private String metadataUri;
    private String transactionHash;
    private Instant mintedAt;

    public CertificateResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public String getTokenId() { return tokenId; }
    public void setTokenId(String tokenId) { this.tokenId = tokenId; }

    public String getMetadataUri() { return metadataUri; }
    public void setMetadataUri(String metadataUri) { this.metadataUri = metadataUri; }

    public String getTransactionHash() { return transactionHash; }
    public void setTransactionHash(String transactionHash) { this.transactionHash = transactionHash; }

    public Instant getMintedAt() { return mintedAt; }
    public void setMintedAt(Instant mintedAt) { this.mintedAt = mintedAt; }

    public static CertificateResponseBuilder builder() {
        return new CertificateResponseBuilder();
    }

    public static class CertificateResponseBuilder {
        private Long id;
        private UUID userId;
        private UUID projectId;
        private String projectName;
        private String tokenId;
        private String metadataUri;
        private String transactionHash;
        private Instant mintedAt;

        public CertificateResponseBuilder id(Long id) { this.id = id; return this; }
        public CertificateResponseBuilder userId(UUID userId) { this.userId = userId; return this; }
        public CertificateResponseBuilder projectId(UUID projectId) { this.projectId = projectId; return this; }
        public CertificateResponseBuilder projectName(String projectName) { this.projectName = projectName; return this; }
        public CertificateResponseBuilder tokenId(String tokenId) { this.tokenId = tokenId; return this; }
        public CertificateResponseBuilder metadataUri(String metadataUri) { this.metadataUri = metadataUri; return this; }
        public CertificateResponseBuilder transactionHash(String transactionHash) { this.transactionHash = transactionHash; return this; }
        public CertificateResponseBuilder mintedAt(Instant mintedAt) { this.mintedAt = mintedAt; return this; }

        public CertificateResponse build() {
            CertificateResponse res = new CertificateResponse();
            res.setId(id);
            res.setUserId(userId);
            res.setProjectId(projectId);
            res.setProjectName(projectName);
            res.setTokenId(tokenId);
            res.setMetadataUri(metadataUri);
            res.setTransactionHash(transactionHash);
            res.setMintedAt(mintedAt);
            return res;
        }
    }
}
