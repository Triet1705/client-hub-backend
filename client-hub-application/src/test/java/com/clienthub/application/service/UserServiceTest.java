package com.clienthub.application.service;

import com.clienthub.application.dto.user.ChangePasswordRequest;
import com.clienthub.application.dto.user.CurrentUserResponse;
import com.clienthub.application.dto.user.UpdateUserPreferencesRequest;
import com.clienthub.common.context.TenantContext;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.entity.UserPreferences;
import com.clienthub.domain.enums.NotificationType;
import com.clienthub.domain.enums.Role;
import com.clienthub.domain.repository.UserPreferencesRepository;
import com.clienthub.domain.repository.UserProfileRepository;
import com.clienthub.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String TENANT_ID = "tenant-test";
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private UserRepository userRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private UserPreferencesRepository userPreferencesRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        userService = new UserService(userRepository, userProfileRepository, userPreferencesRepository, passwordEncoder);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Creates default profile and preferences for current user")
    void getCurrentUser_createsDefaults() {
        User user = createUser();
        when(userRepository.findByIdAndTenantId(USER_ID, TENANT_ID)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserIdAndTenantId(USER_ID, TENANT_ID)).thenReturn(Optional.empty());
        when(userPreferencesRepository.findByUserIdAndTenantId(USER_ID, TENANT_ID)).thenReturn(Optional.empty());
        when(userProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userPreferencesRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CurrentUserResponse response = userService.getCurrentUser(USER_ID);

        assertEquals("dark", response.preferences().theme());
        assertEquals("USD", response.preferences().currency());
        assertFalse(response.profile().publicProfile());
        verify(userProfileRepository).save(any());
        verify(userPreferencesRepository).save(any());
    }

    @Test
    @DisplayName("Notification category preferences suppress matching notifications")
    void allowsNotification_respectsCategory() {
        UserPreferences preferences = new UserPreferences();
        preferences.setNotifyInvoices(false);
        when(userPreferencesRepository.findByUserIdAndTenantId(USER_ID, TENANT_ID)).thenReturn(Optional.of(preferences));

        assertFalse(userService.allowsNotification(USER_ID, TENANT_ID, NotificationType.INVOICE_PAID));
        assertTrue(userService.allowsNotification(USER_ID, TENANT_ID, NotificationType.NEW_COMMENT));
    }

    @Test
    @DisplayName("Password change rejects wrong current password")
    void changePassword_wrongCurrentPassword() {
        User user = createUser();
        user.setPassword("encoded");
        when(userRepository.findByIdAndTenantId(USER_ID, TENANT_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> userService.changePassword(USER_ID, new ChangePasswordRequest("wrong", "NewPass@123")));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Preference update persists changed notification values")
    void updatePreferences_persistsChanges() {
        User user = createUser();
        UserPreferences preferences = new UserPreferences();
        preferences.setTenantId(TENANT_ID);
        preferences.setUser(user);
        when(userRepository.findByIdAndTenantId(USER_ID, TENANT_ID)).thenReturn(Optional.of(user));
        when(userPreferencesRepository.findByUserIdAndTenantId(USER_ID, TENANT_ID)).thenReturn(Optional.of(preferences));
        when(userProfileRepository.findByUserIdAndTenantId(USER_ID, TENANT_ID)).thenReturn(Optional.empty());
        when(userProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        userService.updatePreferences(USER_ID, new UpdateUserPreferencesRequest(
                "light", "EUR", "YYYY-MM-DD", "Asia/Saigon",
                false, true, true, false, true, "19:00", "08:00"
        ));

        assertEquals("light", preferences.getTheme());
        assertEquals("EUR", preferences.getCurrency());
        assertFalse(preferences.isNotifyComments());
        assertFalse(preferences.isNotifyInvoices());
        verify(userPreferencesRepository).save(preferences);
    }

    private User createUser() {
        try {
            User user = User.builder()
                    .tenantId(TENANT_ID)
                    .email("user@test.com")
                    .password("encoded")
                    .fullName("Test User")
                    .role(Role.FREELANCER)
                    .build();
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, USER_ID);
            return user;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
