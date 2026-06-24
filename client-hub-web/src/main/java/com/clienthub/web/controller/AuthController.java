package com.clienthub.web.controller;

import com.clienthub.web.dto.auth.*;
import com.clienthub.web.dto.common.ErrorResponse;
import com.clienthub.application.exception.TenantAlreadyExistsException;
import com.clienthub.domain.entity.RefreshToken;
import com.clienthub.domain.entity.User;
import com.clienthub.application.dto.JwtResponse;
import com.clienthub.application.exception.TokenRefreshException;
import com.clienthub.infrastructure.security.CustomUserDetails;
import com.clienthub.infrastructure.security.JwtTokenProvider;
import com.clienthub.application.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * Controller for authentication endpoints (login, register)
 * Handles JWT token generation and user registration
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Tenant-aware authentication and workspace registration")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.expiration:900000}")
    private long jwtExpirationMs;

    @Value("${app.jwt.refresh-expiration-ms:604800000}")
    private long refreshTokenExpirationMs;

    @Value("${app.auth.refresh-cookie-name:refresh_token}")
    private String refreshCookieName;

    @Value("${app.auth.refresh-cookie-secure:false}")
    private boolean refreshCookieSecure;

    @Value("${app.auth.refresh-cookie-same-site:Lax}")
    private String refreshCookieSameSite;

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
    @Operation(
            summary = "Login with workspace tenant",
            description = "Authenticates an existing user. The X-Tenant-ID header identifies the workspace."
    )
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
            User user = authService.getUserByEmail(userDetails.getEmail(), userDetails.getTenantId());

            String ipAddress = request.getRemoteAddr();
            String userAgent = request.getHeader("User-Agent");

            RefreshToken refreshTokenEntity = authService.createRefreshTokenForUser(user, ipAddress, userAgent);

            Long expiresIn = jwtExpirationMs / 1000;

            log.info("User logged in successfully: {} (role: {})", userDetails.getEmail(), userDetails.getRole());

            JwtResponse response = new JwtResponse(
                    accessToken,
                    null,
                    expiresIn,
                    userDetails.getId(),
                    userDetails.getEmail(),
                    userDetails.getRole(),
                    userDetails.getTenantId()
            );

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(refreshTokenEntity.getToken()).toString())
                    .body(response);

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
     * Register a new user. Role must be CLIENT or FREELANCER (ADMIN cannot self-register).
     *
     * @param registerRequest User registration data (fullName, email, password, role)
     * @param tenantId        Workspace / tenant identifier from X-Tenant-ID header
     * @return RegisterResponse with success message and user info
     */
    @PostMapping("/register")
    @Operation(
            summary = "Create a new workspace and first user",
            description = "Public registration creates a new workspace for an unused X-Tenant-ID. Existing workspaces require invite/admin flows."
    )
    public ResponseEntity<?> registerUser(
            @Valid @RequestBody RegisterRequest registerRequest,
            @Parameter(description = "New workspace tenant slug")
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Tenant-ID", required = false) String tenantId
    ) {
        try {
            User newUser = authService.registerUser(
                    registerRequest.getFullName(),
                    registerRequest.getEmail(),
                    registerRequest.getPassword(),
                    registerRequest.getRole(),
                    tenantId
            );

            log.info("New user registered: {} (id: {}, role: {}, tenant: {})", newUser.getEmail(), newUser.getId(), newUser.getRole(), newUser.getTenantId());

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(new RegisterResponse(
                            "User registered successfully!",
                            newUser.getId(),
                    newUser.getEmail(),
                    newUser.getFullName()
            ));

        } catch (TenantAlreadyExistsException e) {
            log.warn("Registration attempt for existing tenant: {}", tenantId);
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            "Workspace Already Exists",
                            "A workspace with this Tenant ID already exists. Ask an administrator for an invitation.",
                            HttpStatus.CONFLICT.value()
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
    public ResponseEntity<?> refreshToken(
                                          @Valid @RequestBody(required = false) RefreshTokenRequest refreshTokenRequest,
                                          @CookieValue(value = "refresh_token", required = false) String refreshTokenCookie,
                                          HttpServletRequest servletRequest) {
        try {
            String ipAddress = servletRequest.getRemoteAddr();
            String userAgent = servletRequest.getHeader("User-Agent");
            String refreshToken = resolveRefreshToken(refreshTokenRequest, refreshTokenCookie);

            JwtResponse response = authService.refreshToken(
                    refreshToken,
                    ipAddress,
                    userAgent
            );
            String rotatedRefreshToken = response.getRefreshToken();
            response.setRefreshToken(null);

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(rotatedRefreshToken).toString())
                    .body(response);
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
    public ResponseEntity<?> logoutUser(
            @Valid @RequestBody(required = false) RefreshTokenRequest request,
            @CookieValue(value = "refresh_token", required = false) String refreshTokenCookie
    ) {
        authService.logout(resolveRefreshTokenOrNull(request, refreshTokenCookie));

        log.info("User logged out successfully");

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .body(new ErrorResponse(
                        "Logout Successful",
                        "You have been logged out successfully.",
                        HttpStatus.OK.value()
                ));
    }

    private String resolveRefreshToken(RefreshTokenRequest request, String refreshTokenCookie) {
        String token = resolveRefreshTokenOrNull(request, refreshTokenCookie);
        if (token == null || token.isBlank()) {
            throw new TokenRefreshException("", "Refresh token not found.");
        }
        return token;
    }

    private String resolveRefreshTokenOrNull(RefreshTokenRequest request, String refreshTokenCookie) {
        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            return request.getRefreshToken();
        }
        return refreshTokenCookie;
    }

    private ResponseCookie buildRefreshCookie(String refreshToken) {
        return ResponseCookie.from(refreshCookieName, refreshToken)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path("/")
                .maxAge(Duration.ofMillis(refreshTokenExpirationMs))
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(refreshCookieName, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }
}
