package com.clienthub.api.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public class JwtResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("token_type")
    private String tokenType = "Bearer";  // ✅ Default value

    @JsonProperty("expires_in")
    private Long expiresIn;  // ✅ NEW: Seconds until expiry

    private UUID id;
    private String email;
    private String role;

    public JwtResponse() {
    }

    public JwtResponse(String accessToken, String refreshToken, Long expiresIn, UUID id, String email, String role) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = "Bearer";
        this.expiresIn = expiresIn;
        this.id = id;
        this.email = email;
        this.role = role;
    }

    // Getters & Setters
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }

    public Long getExpiresIn() { return expiresIn; }  // ✅ NEW
    public void setExpiresIn(Long expiresIn) { this.expiresIn = expiresIn; }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}