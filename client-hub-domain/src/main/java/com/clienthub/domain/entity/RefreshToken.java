package com.clienthub.domain.entity;

import com.clienthub.common.domain.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_rt_tenant_user_revoked", columnList = "tenant_id, user_id, revoked"),

        @Index(name = "idx_rt_replaced_by", columnList = "replaced_by_token_id"),

        @Index(name = "idx_rt_expiry", columnList = "expire_date"),

        @Index(name = "idx_rt_token", columnList = "token")
})
public class RefreshToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.OTHER)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @NotBlank
    @Size(min = 32, message = "Token must be at least 32 characters")
    @Column(nullable = false, unique = true, length = 500)
    private String token;

    @NotNull
    @Column(name = "expire_date", nullable = false)
    private Instant expiryDate;

    @Column(nullable = false)
    private Boolean revoked = false;

    @JdbcTypeCode(SqlTypes.OTHER)
    @Column(name = "replaced_by_token_id", columnDefinition = "uuid")
    private UUID replacedByTokenId;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    public RefreshToken() {
    }

    public RefreshToken(UUID id, User user, String token, Instant expiryDate, Boolean revoked,
                        UUID replacedByTokenId, Instant lastUsedAt, String ipAddress,
                        String userAgent, String deviceId, String tenantId) {
        this.id = id;
        this.user = user;
        this.token = token;
        this.expiryDate = expiryDate;
        this.revoked = revoked;
        this.replacedByTokenId = replacedByTokenId;
        this.lastUsedAt = lastUsedAt;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.deviceId = deviceId;
        if (tenantId != null) {
            this.setTenantId(tenantId);
        }
    }

    // --- GETTERS & SETTERS ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public Instant getExpiryDate() { return expiryDate; }
    public void setExpiryDate(Instant expiryDate) { this.expiryDate = expiryDate; }

    public Boolean getRevoked() { return revoked; }
    public void setRevoked(Boolean revoked) { this.revoked = revoked; }

    public UUID getReplacedByTokenId() { return replacedByTokenId; }
    public void setReplacedByTokenId(UUID replacedByTokenId) { this.replacedByTokenId = replacedByTokenId; }

    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    // --- HELPER METHODS ---

    public boolean isExpired() {
        return Instant.now().isAfter(this.expiryDate);
    }

    public boolean isValid() {
        return !this.revoked && !isExpired();
    }

    public void revoke(UUID replacedById) {
        this.revoked = true;
        this.replacedByTokenId = replacedById;
    }

    // --- BUILDER ---

    public static RefreshTokenBuilder builder() {
        return new RefreshTokenBuilder();
    }

    public static class RefreshTokenBuilder {
        private UUID id;
        private User user;
        private String token;
        private Instant expiryDate;
        private Boolean revoked = false;
        private UUID replacedByTokenId;
        private Instant lastUsedAt;
        private String ipAddress;
        private String userAgent;
        private String deviceId;
        // 🔴 Fix: Add tenantId to Builder
        private String tenantId;

        RefreshTokenBuilder() { }

        public RefreshTokenBuilder id(UUID id) { this.id = id; return this; }
        public RefreshTokenBuilder user(User user) { this.user = user; return this; }
        public RefreshTokenBuilder token(String token) { this.token = token; return this; }
        public RefreshTokenBuilder expiryDate(Instant expiryDate) { this.expiryDate = expiryDate; return this; }
        public RefreshTokenBuilder revoked(Boolean revoked) { this.revoked = revoked; return this; }
        public RefreshTokenBuilder replacedByTokenId(UUID replacedByTokenId) { this.replacedByTokenId = replacedByTokenId; return this; }
        public RefreshTokenBuilder lastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; return this; }
        public RefreshTokenBuilder ipAddress(String ipAddress) { this.ipAddress = ipAddress; return this; }
        public RefreshTokenBuilder userAgent(String userAgent) { this.userAgent = userAgent; return this; }
        public RefreshTokenBuilder deviceId(String deviceId) { this.deviceId = deviceId; return this; }

        public RefreshTokenBuilder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public RefreshToken build() {
            return new RefreshToken(id, user, token, expiryDate, revoked, replacedByTokenId, lastUsedAt, ipAddress, userAgent, deviceId, tenantId);
        }
    }
}