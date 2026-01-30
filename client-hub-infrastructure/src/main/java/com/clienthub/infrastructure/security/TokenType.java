package com.clienthub.infrastructure.security;

public enum TokenType {
    /**
     * Access Token - Short-lived token for API authentication
     * Default expiration: 15 minutes
     */
    ACCESS,
    
    /**
     * Refresh Token - Long-lived token for obtaining new access tokens
     * Default expiration: 24 hours
     */
    REFRESH;
    
    /**
     * Get token type as string for JWT claims
     */
    public String getValue() {
        return this.name();
    }
}
