package com.clienthub.core.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final String CLAIM_IMPERSONATOR_ID = "impersonator_id";

    @Value("${jwt.secret:default_secret_key_must_be_at_least_32_characters_long_for_hs256}")
    private String jwtSecret;

    @Value("${jwt.expiration:900000}") // 15 minutes default
    private long jwtExpirationMs;

    @Value("${jwt.refresh-expiration:604800000}") // 7 days default
    private long refreshExpirationMs;

    /**
     * Generate secret key from configured secret string
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generate JWT Access Token from CustomUserDetails
     * * @param userDetails CustomUserDetails containing user info
     * @return JWT token string
     */
    public String generateAccessToken(CustomUserDetails userDetails) {
        return generateAccessToken(
                userDetails.getId(),
                userDetails.getEmail(),
                userDetails.getRole(),
                userDetails.getTenantId()
        );
    }

    /**
     * Generate JWT Access Token
     * * @param userId User's unique identifier
     * @param email User's email
     * @param role User's role (FREELANCER, CLIENT, ADMIN)
     * @return JWT token string
     */
    public String generateAccessToken(UUID userId, String email, String role, String tenantId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId.toString());
        claims.put("email", email);
        claims.put("role", role);
        claims.put("tenantId", tenantId);
        claims.put("type", TokenType.ACCESS.getValue());

        return createToken(claims, userId.toString(), jwtExpirationMs);
    }

    /**
     * Generate Impersonation Token (For Admin Support)
     * * @param targetUserId The user ID being impersonated
     * @param targetEmail The user email being impersonated
     * @param targetRole The user role
     * @param targetTenantId The user tenant
     * @param adminId The ID of the Admin performing the impersonation
     * @return JWT token string with impersonator claim
     */
    public String generateImpersonationToken(UUID targetUserId, String targetEmail, String targetRole, String targetTenantId, UUID adminId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", targetUserId.toString());
        claims.put("email", targetEmail);
        claims.put("role", targetRole);
        claims.put("tenantId", targetTenantId);
        claims.put("type", TokenType.ACCESS.getValue());

        claims.put(CLAIM_IMPERSONATOR_ID, adminId.toString());

        logger.warn("Security Alert: Generating Impersonation Token. Admin {} is impersonating User {}", adminId, targetUserId);

        return createToken(claims, targetUserId.toString(), jwtExpirationMs);
    }

    /**
     * Get JWT expiration time in milliseconds
     */
    public long getExpirationTime() {
        return jwtExpirationMs;
    }

    /**
     * Generate JWT Refresh Token (longer expiration)
     * * @param userId User's unique identifier
     * @return Refresh token string
     */
    public String generateRefreshToken(UUID userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", TokenType.REFRESH.getValue());

        return createToken(claims, userId.toString(), refreshExpirationMs);
    }

    /**
     * Create JWT Token with custom claims and expiration
     */
    private String createToken(Map<String, Object> claims, String subject, long expirationTime) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Extract username (subject) from token
     */
    public UUID extractUserId(String token) {
        String userIdStr = extractClaim(token, Claims::getSubject);
        return UUID.fromString(userIdStr);
    }

    /**
     * Extract email from token claims
     */
    public String extractEmail(String token) {
        return extractClaim(token, claims -> claims.get("email", String.class));
    }

    /**
     * Extract role from token claims
     */
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public String extractTenantId(String token) {
        return extractClaim(token, claims -> claims.get("tenantId", String.class));
    }

    /**
     * Extract Impersonator ID if exists
     * Used for Audit Logging to trace actions back to the Admin
     */
    public UUID extractImpersonatorId(String token) {
        String impersonatorIdStr = extractClaim(token, claims -> claims.get(CLAIM_IMPERSONATOR_ID, String.class));
        return impersonatorIdStr != null ? UUID.fromString(impersonatorIdStr) : null;
    }

    /**
     * Extract token type from token claims
     */
    public TokenType extractTokenType(String token) {
        String type = extractClaim(token, claims -> claims.get("type", String.class));
        return TokenType.valueOf(type);
    }

    /**
     * Extract expiration date from token
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Generic method to extract any claim from token
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Parse and extract all claims from token
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Check if token is expired
     */
    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Validate token against user details
     * * @param token JWT token string
     * @param userId Expected user ID
     * @return true if token is valid and matches user
     */
    public Boolean validateToken(String token, UUID userId) {
        try {
            final UUID extractedUserId = extractUserId(token);
            return (extractedUserId.equals(userId) && !isTokenExpired(token));
        } catch (JwtException | IllegalArgumentException e) {
            // Token validation failed (signature invalid, malformed, expired, etc.)
            logger.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validate token without user context (used for refresh tokens)
     */
    public Boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}