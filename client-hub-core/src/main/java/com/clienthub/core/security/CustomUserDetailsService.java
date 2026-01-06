package com.clienthub.core.security;

import com.clienthub.core.domain.entity.User;
import com.clienthub.core.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

/**
 * CHDEV-35: Implement CustomUserDetailsService loading User by email.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    // Manual Constructor Injection (Thay vì @RequiredArgsConstructor của Lombok)
    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // 1. Tìm user trong DB theo email
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        // 2. Map Role của hệ thống sang Authority của Spring Security
        // Ví dụ: User có role CLIENT -> Authority là "ROLE_CLIENT"
        var authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());

        // 3. Trả về đối tượng UserDetails chuẩn của Spring
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(Collections.singletonList(authority))
                .accountExpired(false)
                .accountLocked(!user.isActive()) // Sử dụng getter thủ công isActive()
                .credentialsExpired(false)
                .disabled(!user.isActive())
                .build();
    }
}