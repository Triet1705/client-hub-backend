package com.clienthub.infrastructure.security;

import com.clienthub.domain.entity.*;
import com.clienthub.domain.enums.*;

import com.clienthub.common.context.TenantContext;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, CustomUserDetailsService userDetailsService) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            // Early return if no token or invalid token
            if (!StringUtils.hasText(jwt) || !tokenProvider.validateToken(jwt)) {
                filterChain.doFilter(request, response);
                return;
            }

            // Skip if already authenticated (avoid redundant database queries)
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                filterChain.doFilter(request, response);
                return;
            }

            // Validate token type - only accept ACCESS tokens
            TokenType tokenType = tokenProvider.extractTokenType(jwt);
            if (tokenType != TokenType.ACCESS) {
                log.warn("Rejected non-ACCESS token type: {}", tokenType);
                filterChain.doFilter(request, response);
                return;
            }

            // Extract user information from token
            UUID userId = tokenProvider.extractUserId(jwt);
            String userEmail = tokenProvider.extractEmail(jwt);
            String tenantId = tokenProvider.extractTenantId(jwt);

            log.debug("Authenticating user: {} (ID: {}, Tenant: {})", userEmail, userId, tenantId);

            if (tenantId != null && !tenantId.isBlank()) {
                TenantContext.setTenantId(tenantId);
                log.debug("TenantContext set to: {}", tenantId);
            } else {
                log.warn("No tenantId found in JWT token for user: {}", userEmail);
            }

            // Load user details and create authentication
            UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );

            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // Set authentication in security context
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Successfully authenticated user: {} (ID: {})", userEmail, userId);

        } catch (JwtException ex) {
            log.error("Invalid JWT token: {}", ex.getMessage());
        } catch (UsernameNotFoundException ex) {
            log.warn("User not found in database (may have been deleted): {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty: {}", ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected error during authentication", ex);
        } finally {
            TenantContext.clear();
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(7);
        }
        return null;
    }
}