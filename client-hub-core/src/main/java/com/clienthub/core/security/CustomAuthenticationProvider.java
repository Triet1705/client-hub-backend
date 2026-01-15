package com.clienthub.core.security;

import com.clienthub.core.domain.entity.User;
import com.clienthub.core.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class CustomAuthenticationProvider extends DaoAuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(CustomAuthenticationProvider.class);

    private final UserRepository userRepository;

    @Value("${security.account-lockout.max-attempts:5}")
    private int maxFailedAttempts;

    @Value("${security.account-lockout.lock-duration-minutes:15}")
    private long lockDurationMinutes;

    public CustomAuthenticationProvider(
            UserRepository userRepository,
            UserDetailsService userDetailsService
    ) {
        this.userRepository = userRepository;
        setUserDetailsService(userDetailsService);
    }

    // Spring will auto-inject PasswordEncoder through this setter
    @Override
    public void setPasswordEncoder(PasswordEncoder passwordEncoder) {
        super.setPasswordEncoder(passwordEncoder);
    }

    @Override
    @Transactional
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = authentication.getName();
        
        User user = userRepository.findByEmailIgnoringTenant(email)
                .orElse(null);
        
        if (user != null) {
            if (user.isAccountLocked()) {
                log.warn("Login attempt for locked account: {} (locked until: {})", 
                    email, user.getAccountLockedUntil());
                
                throw new LockedException(
                    String.format("Account is locked until %s. Please try again later or contact support.", 
                        user.getAccountLockedUntil())
                );
            }
        }

        try {
            Authentication result = super.authenticate(authentication);
            
            if (user != null) {
                resetFailedAttempts(user);
            }
            
            return result;
            
        } catch (BadCredentialsException e) {
            if (user != null) {
                incrementFailedAttempts(user);
            }
            throw e;
        }
    }

    @Transactional
    protected void resetFailedAttempts(User user) {
        if (user.getFailedLoginAttempts() > 0 || user.getAccountLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setAccountLockedUntil(null);
            userRepository.save(user);
            
            log.info("Reset failed login attempts for user: {}", user.getEmail());
        }
    }

    @Transactional
    protected void incrementFailedAttempts(User user) {
        int attempts = (user.getFailedLoginAttempts() != null ? user.getFailedLoginAttempts() : 0) + 1;
        user.setFailedLoginAttempts(attempts);
        
        if (attempts >= maxFailedAttempts) {
            Instant lockUntil = Instant.now().plus(lockDurationMinutes, ChronoUnit.MINUTES);
            user.setAccountLockedUntil(lockUntil);
            
            log.warn("Account locked due to {} failed login attempts: {} (locked until: {})", 
                attempts, user.getEmail(), lockUntil);
        } else {
            log.warn("Failed login attempt {}/{} for user: {}", 
                attempts, maxFailedAttempts, user.getEmail());
        }
        
        userRepository.save(user);
    }
}
