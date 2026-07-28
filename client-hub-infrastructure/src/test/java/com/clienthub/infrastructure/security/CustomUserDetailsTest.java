package com.clienthub.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomUserDetailsTest {

    @Test
    void inactiveAccountMapsToDisabledRatherThanLocked() {
        CustomUserDetails details =
                new CustomUserDetails(
                        UUID.randomUUID(),
                        "inactive@example.test",
                        "encoded",
                        "CLIENT",
                        false,
                        "tenant-a",
                        List.of());

        assertFalse(details.isEnabled());
        assertTrue(details.isAccountNonLocked());
    }
}
