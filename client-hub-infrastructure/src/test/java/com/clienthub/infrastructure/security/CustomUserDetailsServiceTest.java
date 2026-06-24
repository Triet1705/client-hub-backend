package com.clienthub.infrastructure.security;

import com.clienthub.common.context.TenantContext;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.Role;
import com.clienthub.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    private static final String EMAIL = "alex@example.com";
    private static final String TENANT_ID = "agency-alpha";

    @Mock private UserRepository userRepository;

    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService = new CustomUserDetailsService(userRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("User details lookup fails without tenant context")
    void loadUserByUsernameRejectsMissingTenantContext() {
        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername(EMAIL));

        verify(userRepository, never()).findByEmailIgnoringTenant(EMAIL);
        verify(userRepository, never()).findByEmailCustom(anyString(), anyString());
    }

    @Test
    @DisplayName("User details lookup does not fall back to another tenant")
    void loadUserByUsernameRejectsWrongTenant() {
        TenantContext.setTenantId("agency-beta");
        when(userRepository.findByEmailCustom(EMAIL, "agency-beta")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername(EMAIL));

        verify(userRepository).findByEmailCustom(EMAIL, "agency-beta");
        verify(userRepository, never()).findByEmailIgnoringTenant(EMAIL);
    }

    @Test
    @DisplayName("User details lookup uses current tenant")
    void loadUserByUsernameUsesCurrentTenant() {
        TenantContext.setTenantId(TENANT_ID);
        User user = User.builder()
                .tenantId(TENANT_ID)
                .email(EMAIL)
                .password("encoded")
                .role(Role.CLIENT)
                .build();
        when(userRepository.findByEmailCustom(EMAIL, TENANT_ID)).thenReturn(Optional.of(user));

        CustomUserDetails details = (CustomUserDetails) userDetailsService.loadUserByUsername(EMAIL);

        assertEquals(TENANT_ID, details.getTenantId());
        verify(userRepository).findByEmailCustom(EMAIL, TENANT_ID);
        verify(userRepository, never()).findByEmailIgnoringTenant(EMAIL);
    }
}
