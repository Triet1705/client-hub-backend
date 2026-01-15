package com.clienthub.core.service;

import com.clienthub.core.dto.JwtResponse;
import com.clienthub.core.domain.entity.RefreshToken;
import com.clienthub.core.domain.entity.User;
import com.clienthub.core.domain.enums.Role;
import com.clienthub.core.exception.TokenRefreshException;
import com.clienthub.core.repository.RefreshTokenRepository;
import com.clienthub.core.repository.UserRepository;
import com.clienthub.core.security.CustomUserDetails;
import com.clienthub.core.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class AuthService {

    private static final String TOKEN_TYPE_BEARER = "Bearer";
    private static final int REFRESH_TOKEN_BYTE_LENGTH = 64;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.jwt.refresh-expiration-ms:604800000}")
    private Long refreshTokenDurationMs;

    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(RefreshTokenRepository refreshTokenRepository,
                       UserRepository userRepository,
                       JwtTokenProvider jwtTokenProvider,
                       PasswordEncoder passwordEncoder
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User registerUser(String fullName, String email, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(Role.CLIENT);
        user.setTenantId(email);

        return userRepository.save(user);
    }
    @Transactional
    public void logout(String refreshTokenStr) {
        if (refreshTokenStr == null || refreshTokenStr.isEmpty()) {
            return;
        }
        refreshTokenRepository.findByToken(refreshTokenStr)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    @Transactional
    public RefreshToken createRefreshTokenForUser(User user, String ipAddress, String userAgent) {
        return createRefreshToken(user, ipAddress, userAgent);
    }
    
    // TODO
    // Race Condition Risk: Concurrent refresh requests với cùng token
    // có thể tạo ra 2 valid tokens. Solution: Optimistic Locking (@Version)
    // hoặc Pessimistic Locking (SELECT FOR UPDATE).
    // Priority: P2 (implement before production deployment)
    
    @Transactional
    public JwtResponse refreshToken(String requestToken, String ipAddress, String userAgent) {
        return refreshTokenRepository.findByToken(requestToken)
                .map(parentToken -> {
                    if (parentToken.getRevoked()) {
                        refreshTokenRepository.deleteByUser(parentToken.getUser());
                        throw new TokenRefreshException(requestToken,
                                "Security Alert: Reuse of revoked token detected. All sessions invalidated.");
                    }
                    return parentToken;
                })
                .map(this::verifyExpiration) 
                .map(parentToken -> {
                    User user = parentToken.getUser();

                    RefreshToken childToken = createRefreshToken(user, ipAddress, userAgent);

                    parentToken.setRevoked(true);
                    parentToken.setReplacedByTokenId(childToken.getId());
                    parentToken.setLastUsedAt(Instant.now());

                    refreshTokenRepository.save(parentToken);

                    CustomUserDetails userDetails = CustomUserDetails.build(user);
                    String newAccessToken = jwtTokenProvider.generateAccessToken(userDetails);
                    long expiresIn = jwtTokenProvider.getExpirationTime() / 1000;

                    return new JwtResponse(
                            newAccessToken,
                            childToken.getToken(),
                            expiresIn,
                            user.getId(),
                            user.getEmail(),
                            user.getRole().name(),
                            user.getTenantId()
                    );
                })
                .orElseThrow(() -> new TokenRefreshException(requestToken, 
                    "Refresh token not found in database."));
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));
    }

    public boolean emailExists(String email) {
        return userRepository.existsByEmail(email);
    }


    private RefreshToken createRefreshToken(User user, String ipAddress, String userAgent) {
        byte[] randomBytes = new byte[REFRESH_TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        String secureToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tenantId(user.getTenantId())
                .token(secureToken)
                .expiryDate(Instant.now().plusMillis(refreshTokenDurationMs))
                .revoked(false)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    private RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token);
            throw new TokenRefreshException(token.getToken(), "Refresh token was expired. Please make a new signin request");
        }
        return token;
    }

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void purgeExpiredTokens() {
        Instant now = Instant.now();
        refreshTokenRepository.deleteAllByExpiryDateBefore(now);
    }

    @Transactional
    public void unlockAccount(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));
        
        if (user.getAccountLockedUntil() != null || user.getFailedLoginAttempts() > 0) {
            user.setAccountLockedUntil(null);
            user.setFailedLoginAttempts(0);
            userRepository.save(user);
        }
    }

    public boolean isAccountLocked(String email) {
        return userRepository.findByEmail(email)
                .map(User::isAccountLocked)
                .orElse(false);
    }
}