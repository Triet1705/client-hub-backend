package com.clienthub.infrastructure.security;

import com.clienthub.common.context.TenantContext;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;


@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = findUserForCurrentTenant(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        var authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());

        return new CustomUserDetails(
                user.getId(),           
                user.getEmail(),        
                user.getPasswordHash(), 
                user.getRole().name(),  
                user.isActive(),
                user.getTenantId(),
                Collections.singletonList(authority)  
        );
    }

    private java.util.Optional<User> findUserForCurrentTenant(String email) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId != null && !tenantId.isBlank() && !TenantContext.SYSTEM_TENANT.equals(tenantId)) {
            return userRepository.findByEmailCustom(email, tenantId);
        }
        return userRepository.findByEmailIgnoringTenant(email);
    }
}
