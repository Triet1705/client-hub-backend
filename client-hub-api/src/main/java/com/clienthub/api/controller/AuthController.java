package com.clienthub.api.controller;

import com.clienthub.api.dto.auth.JwtResponse;
import com.clienthub.api.dto.auth.LoginRequest;
import com.clienthub.api.dto.auth.RegisterRequest;
import com.clienthub.api.dto.auth.RegisterResponse;
import com.clienthub.api.dto.common.ErrorResponse;
import com.clienthub.core.domain.entity.User;
import com.clienthub.core.security.CustomUserDetails;
import com.clienthub.core.security.JwtTokenProvider;
import com.clienthub.core.service.AuthService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for authentication endpoints (login, register)
 * Handles JWT token generation and user registration
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.expiration:900000}") // 15 minutes default (in milliseconds)
    private long jwtExpirationMs;

    public AuthController(
            AuthenticationManager authenticationManager,
            AuthService authService,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.authenticationManager = authenticationManager;
        this.authService = authService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * POST /api/auth/login
     * Authenticate user and return JWT tokens
     *
     * @param loginRequest Login credentials (email, password)
     * @return JwtResponse with access token, refresh token, and user info
     */
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            // 1. Authenticate using Spring Security AuthenticationManager
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );

            // 2. Set authentication in SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 3. Get CustomUserDetails from authentication principal
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

            // 4. Generate JWT tokens using data from CustomUserDetails
            String accessToken = jwtTokenProvider.generateAccessToken(
                    userDetails.getId(),
                    userDetails.getEmail(),
                    userDetails.getRole()
            );
            String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails.getId());

            // 5. Calculate expires_in (convert milliseconds to seconds)
            Long expiresIn = jwtExpirationMs / 1000;

            log.info("User logged in successfully: {} (role: {})", userDetails.getEmail(), userDetails.getRole());

            // 6. Return JWT response
            return ResponseEntity.ok(new JwtResponse(
                    accessToken,
                    refreshToken,
                    expiresIn,
                    userDetails.getId(),
                    userDetails.getEmail(),
                    userDetails.getRole()
            ));

        }catch (DisabledException e) {
            log.warn("Login attempt with disabled account: {}", loginRequest.getEmail());
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(
                            "Account Disabled",
                            "Your account has been deactivated. Please contact support.",
                            HttpStatus.FORBIDDEN.value()
                    ));
        } catch (BadCredentialsException e) {
            log.warn("Failed login attempt for email: {}", loginRequest.getEmail());
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(
                            "Invalid Credentials",
                            "Email or password is incorrect",
                            HttpStatus.UNAUTHORIZED.value()
                    ));
        } catch (Exception e) {
            log.error("Login error for email: {}", loginRequest.getEmail(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(
                            "Login Failed",
                            "An error occurred during login. Please try again.",
                            HttpStatus.INTERNAL_SERVER_ERROR.value()
                    ));
        }
    }

    /**
     * POST /api/auth/register
     * Register a new user with default CLIENT role
     *
     * @param registerRequest User registration data (fullName, email, password)
     * @return RegisterResponse with success message and user info
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        try {
            // 1. Check if email already exists
            if (authService.emailExists(registerRequest.getEmail())) {
                log.warn("Registration attempt with existing email: {}", registerRequest.getEmail());
                return ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .body(new ErrorResponse(
                                "Email Already Exists",
                                "An account with this email already exists. Please login or use a different email.",
                                HttpStatus.CONFLICT.value()
                        ));
            }

            // 2. Register new user
            User newUser = authService.registerUser(
                    registerRequest.getFullName(),
                    registerRequest.getEmail(),
                    registerRequest.getPassword()
            );

            log.info("New user registered: {} (id: {})", newUser.getEmail(), newUser.getId());

            // 3. Return success response
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(new RegisterResponse(
                            "User registered successfully!",
                            newUser.getId(),
                            newUser.getEmail(),
                            newUser.getFullName()
                    ));

        } catch (IllegalArgumentException e) {
            log.error("Registration validation error: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(
                            "Registration Failed",
                            e.getMessage(),
                            HttpStatus.BAD_REQUEST.value()
                    ));
        } catch (Exception e) {
            log.error("Registration error for email: {}", registerRequest.getEmail(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(
                            "Registration Failed",
                            "An error occurred during registration. Please try again.",
                            HttpStatus.INTERNAL_SERVER_ERROR.value()
                    ));
        }
    }
}
