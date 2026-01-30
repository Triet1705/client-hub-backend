package com.clienthub.web.controller;

import com.clienthub.web.dto.auth.*;
import com.clienthub.web.dto.common.ErrorResponse;
import com.clienthub.domain.entity.RefreshToken;
import com.clienthub.domain.entity.User;
import com.clienthub.application.dto.JwtResponse;
import com.clienthub.application.exception.TokenRefreshException;
import com.clienthub.infrastructure.security.CustomUserDetails;
import com.clienthub.infrastructure.security.JwtTokenProvider;
import com.clienthub.application.service.AuthService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
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

    @Value("${jwt.expiration:900000}")
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
     * @param request HTTP request to extract IP address and user agent
     * @return JwtResponse with access token, refresh token, and user info
     */
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest,
                                              HttpServletRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

            String accessToken = jwtTokenProvider.generateAccessToken(
                    userDetails.getId(),
                    userDetails.getEmail(),
                    userDetails.getRole(),
                    userDetails.getTenantId()
            );
            User user = authService.getUserByEmail(userDetails.getEmail());

            String ipAddress = request.getRemoteAddr();
            String userAgent = request.getHeader("User-Agent");

            RefreshToken refreshTokenEntity = authService.createRefreshTokenForUser(user, ipAddress, userAgent);

            Long expiresIn = jwtExpirationMs / 1000;

            log.info("User logged in successfully: {} (role: {})", userDetails.getEmail(), userDetails.getRole());

            return ResponseEntity.ok(new JwtResponse(
                    accessToken,
                    refreshTokenEntity.getToken(),
                    expiresIn,
                    userDetails.getId(),
                    userDetails.getEmail(),
                    userDetails.getRole(),
                    userDetails.getTenantId()
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

            User newUser = authService.registerUser(
                    registerRequest.getFullName(),
                    registerRequest.getEmail(),
                    registerRequest.getPassword()
            );

            log.info("New user registered: {} (id: {})", newUser.getEmail(), newUser.getId());

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

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshTokenRequest refreshTokenRequest,
                                          HttpServletRequest servletRequest) {
        try {
            String ipAddress = servletRequest.getRemoteAddr();
            String userAgent = servletRequest.getHeader("User-Agent");

            JwtResponse response = authService.refreshToken(
                    refreshTokenRequest.getRefreshToken(),
                    ipAddress,
                    userAgent
            );
            return ResponseEntity.ok(response);
        } catch (TokenRefreshException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(
                            "Token Refresh Failed",
                            e.getMessage(),
                            HttpStatus.FORBIDDEN.value()
                    ));
        }
    }

    /**
     * POST /api/auth/logout
     * Logout user by revoking their refresh token
     * CHDEV-101: Logout Endpoint
     *
     * @param request Refresh token to revoke
     * @return Success message
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());

        log.info("User logged out successfully");

        return ResponseEntity.ok(new ErrorResponse(
                "Logout Successful",
                "You have been logged out successfully.",
                HttpStatus.OK.value()
        ));
    }
}
