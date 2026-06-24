package com.clienthub.application.service;

import com.clienthub.application.exception.TenantAlreadyExistsException;
import com.clienthub.common.context.TenantContext;
import com.clienthub.domain.entity.Tenant;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.Role;
import com.clienthub.domain.repository.RefreshTokenRepository;
import com.clienthub.domain.repository.TenantRepository;
import com.clienthub.domain.repository.UserRepository;
import com.clienthub.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String TENANT_ID = "agency-alpha";

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                refreshTokenRepository,
                userRepository,
                tenantRepository,
                jwtTokenProvider,
                passwordEncoder
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Registration creates a new tenant and first user for unused workspace slug")
    void registerUserCreatesTenantAndUser() {
        when(tenantRepository.existsById(TENANT_ID)).thenReturn(false);
        when(passwordEncoder.encode("Password@123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User created = authService.registerUser(
                "Alex Rivera",
                "alex@example.com",
                "Password@123",
                "CLIENT",
                TENANT_ID
        );

        assertEquals(TENANT_ID, created.getTenantId());
        assertEquals("alex@example.com", created.getEmail());
        assertEquals(Role.CLIENT, created.getRole());
        assertEquals("encoded", created.getPasswordHash());

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        assertEquals(TENANT_ID, tenantCaptor.getValue().getId());
        assertEquals(Tenant.STATUS_ACTIVE, tenantCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("Registration rejects an existing workspace slug")
    void registerUserRejectsExistingTenant() {
        when(tenantRepository.existsById(TENANT_ID)).thenReturn(true);

        assertThrows(TenantAlreadyExistsException.class,
                () -> authService.registerUser(
                        "Alex Rivera",
                        "alex@example.com",
                        "Password@123",
                        "CLIENT",
                        TENANT_ID
                ));

        verify(tenantRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Auth lookup requires an explicit tenant context")
    void getUserByEmailRejectsMissingTenant() {
        assertThrows(IllegalArgumentException.class,
                () -> authService.getUserByEmail("alex@example.com", null));

        verify(userRepository, never()).findByEmailCustom(anyString(), anyString());
        verify(userRepository, never()).findByEmailIgnoringTenant(anyString());
    }

    @Test
    @DisplayName("Auth lookup uses tenant-scoped query only")
    void getUserByEmailUsesTenantScopedQuery() {
        User user = User.builder()
                .tenantId(TENANT_ID)
                .email("alex@example.com")
                .password("encoded")
                .role(Role.CLIENT)
                .build();
        when(userRepository.findByEmailCustom("alex@example.com", TENANT_ID)).thenReturn(Optional.of(user));

        User found = authService.getUserByEmail("alex@example.com", TENANT_ID);

        assertEquals(TENANT_ID, found.getTenantId());
        verify(userRepository).findByEmailCustom("alex@example.com", TENANT_ID);
        verify(userRepository, never()).findByEmailIgnoringTenant(anyString());
    }

    @Test
    @DisplayName("Invalid tenant slug is rejected before persistence")
    void registerUserRejectsInvalidTenantSlug() {
        assertThrows(IllegalArgumentException.class,
                () -> authService.registerUser(
                        "Alex Rivera",
                        "alex@example.com",
                        "Password@123",
                        "CLIENT",
                        "Bad_Tenant"
                ));

        verifyNoInteractions(tenantRepository);
        verifyNoInteractions(userRepository);
    }
}
