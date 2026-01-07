package com.clienthub.core.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public class CustomUserDetails implements UserDetails {

    private final String id;
    private final String email;
    private final String password;
    private final String role;
    private final boolean active;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(
            String id,
            String email,
            String password,
            String role,
            boolean active,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.role = role;
        this.active = active;
        this.authorities = authorities;
    }


    /**
     * Get user's unique ID (UUID)
     * Used for JWT token generation without additional DB query
     */
    public String getId() {
        return id;
    }

    /**
     * Get user's email (same as username in our system)
     */
    public String getEmail() {
        return email;
    }

    /**
     * Get user's role name (CLIENT, FREELANCER, ADMIN)
     * Used for JWT token generation without additional DB query
     */
    public String getRole() {
        return role;
    }


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        // In our system, username = email
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;  // We don't use account expiration
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;  // Locked if user is inactive
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;  // We don't use credential expiration
    }

    @Override
    public boolean isEnabled() {
        return active;  // Enabled if user is active
    }
}
