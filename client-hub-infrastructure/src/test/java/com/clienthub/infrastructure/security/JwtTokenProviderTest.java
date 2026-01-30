package com.clienthub.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Tests for JwtTokenProvider
 * 
 * Tests:
 * - Token generation
 * - Token validation
 * - Claims extraction
 * - Expiration handling
 */
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private static final String TEST_SECRET = "test_secret_key_must_be_at_least_32_characters_long_for_hs256_algorithm";
    private static final UUID TEST_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_ROLE = "FREELANCER";
    private static final String TEST_TENANT_ID = "tenant-test-123";

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        
        // Inject test values using ReflectionTestUtils
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", 900000L); // 15 minutes
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshExpirationMs", 86400000L); // 24 hours
    }

    @Test
    void shouldGenerateAccessToken() {
        // When
        String token = jwtTokenProvider.generateAccessToken(TEST_USER_ID, TEST_EMAIL, TEST_ROLE, TEST_TENANT_ID);

        // Then
        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3); // JWT has 3 parts: header.payload.signature
        System.out.println("Generated Token: " + token);
    }

    @Test
    void shouldExtractUserIdFromToken() {
        // Given
        String token = jwtTokenProvider.generateAccessToken(TEST_USER_ID, TEST_EMAIL, TEST_ROLE, TEST_TENANT_ID);

        // When
        UUID extractedUserId = jwtTokenProvider.extractUserId(token);

        // Then
        assertEquals(TEST_USER_ID, extractedUserId);
    }

    @Test
    void shouldExtractEmailFromToken() {
        // Given
        String token = jwtTokenProvider.generateAccessToken(TEST_USER_ID, TEST_EMAIL, TEST_ROLE, TEST_TENANT_ID);

        // When
        String extractedEmail = jwtTokenProvider.extractEmail(token);

        // Then
        assertEquals(TEST_EMAIL, extractedEmail);
    }

    @Test
    void shouldExtractRoleFromToken() {
        // Given
        String token = jwtTokenProvider.generateAccessToken(TEST_USER_ID, TEST_EMAIL, TEST_ROLE, TEST_TENANT_ID);

        // When
        String extractedRole = jwtTokenProvider.extractRole(token);

        // Then
        assertEquals(TEST_ROLE, extractedRole);
    }

    @Test
    void shouldExtractTenantIdFromToken() {
        // Given
        String token = jwtTokenProvider.generateAccessToken(TEST_USER_ID, TEST_EMAIL, TEST_ROLE, TEST_TENANT_ID);

        // When
        String extractedTenantId = jwtTokenProvider.extractTenantId(token);

        // Then
        assertEquals(TEST_TENANT_ID, extractedTenantId);
    }

    @Test
    void shouldValidateCorrectToken() {
        // Given
        String token = jwtTokenProvider.generateAccessToken(TEST_USER_ID, TEST_EMAIL, TEST_ROLE, TEST_TENANT_ID);

        // When
        Boolean isValid = jwtTokenProvider.validateToken(token, TEST_USER_ID);

        // Then
        assertTrue(isValid);
    }

    @Test
    void shouldRejectTokenWithWrongUserId() {
        // Given
        String token = jwtTokenProvider.generateAccessToken(TEST_USER_ID, TEST_EMAIL, TEST_ROLE, TEST_TENANT_ID);

        // When
        Boolean isValid = jwtTokenProvider.validateToken(token, UUID.fromString("650e8400-e29b-41d4-a716-446655440001"));

        // Then
        assertFalse(isValid);
    }

    @Test
    void shouldRejectMalformedToken() {
        // Given
        String malformedToken = "this.is.not.a.valid.jwt";

        // When
        Boolean isValid = jwtTokenProvider.validateToken(malformedToken, TEST_USER_ID);

        // Then
        assertFalse(isValid);
    }

    @Test
    void shouldGenerateRefreshToken() {
        // When
        String refreshToken = jwtTokenProvider.generateRefreshToken(TEST_USER_ID);

        // Then
        assertNotNull(refreshToken);
        assertTrue(jwtTokenProvider.validateToken(refreshToken));
    }

    @Test
    void shouldRejectExpiredToken() throws InterruptedException {
        // Given - Generate token with 1ms expiration
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", 1L);
        String token = jwtTokenProvider.generateAccessToken(TEST_USER_ID, TEST_EMAIL, TEST_ROLE, TEST_TENANT_ID);

        // Wait for token to expire
        Thread.sleep(10);

        // When
        Boolean isValid = jwtTokenProvider.validateToken(token, TEST_USER_ID);

        // Then
        assertFalse(isValid, "Expired token should be invalid");
    }
}
