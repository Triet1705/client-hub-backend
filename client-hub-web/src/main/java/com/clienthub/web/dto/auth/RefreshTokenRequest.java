package com.clienthub.web.dto.auth;

public class RefreshTokenRequest {
    // Optional for browser sessions because the authoritative refresh token is
    // supplied by the backend-managed HttpOnly cookie.
    private String refreshToken;

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
