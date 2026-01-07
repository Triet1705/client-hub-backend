package com.clienthub.core.service;

import com.clienthub.core.domain.entity.User;
import com.clienthub.core.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for authentication and user management
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Register a new user with default CLIENT role
     *
     * @param fullName User's full name
     * @param email User's email
     * @param password User's plain password (will be encoded)
     * @return Created user entity
     * @throws IllegalArgumentException if email already exists
     */
    @Transactional
    public User registerUser(String fullName, String email, String password) {
        // Check if email already exists
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        // Create new user with default CLIENT role
        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(User.Role.CLIENT);  // Default role for registration

        return userRepository.save(user);
    }

    /**
     * Find user by email
     *
     * @param email User's email
     * @return User entity
     * @throws IllegalArgumentException if user not found
     */
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));
    }

    /**
     * Check if email is already registered
     *
     * @param email Email to check
     * @return true if exists, false otherwise
     */
    public boolean emailExists(String email) {
        return userRepository.existsByEmail(email);
    }
}
