package com.clienthub.infrastructure.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ProxyManager<byte[]> proxyManager;
    private final JwtTokenProvider tokenProvider;

    @Value("${rate-limit.login:5}")
    private int loginLimit;

    @Value("${rate-limit.register:3}")
    private int registerLimit;

    @Value("${rate-limit.ai:10}")
    private int aiLimit;

    @Value("${rate-limit.general:60}")
    private int generalLimit;

    public RateLimitFilter(ProxyManager<byte[]> proxyManager, JwtTokenProvider tokenProvider) {
        this.proxyManager = proxyManager;
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (path.startsWith("/api/")) {
            String key;
            int limit;
            
            if ("POST".equals(method) && path.equals("/api/auth/login")) {
                key = "login:" + getClientIP(request);
                limit = loginLimit;
            } else if ("POST".equals(method) && path.equals("/api/auth/register")) {
                key = "register:" + getClientIP(request);
                limit = registerLimit;
            } else if ("POST".equals(method) && path.equals("/api/auth/refresh")) {
                key = "refresh:" + getClientIP(request);
                limit = 10;
            } else if (path.startsWith("/api/ai/")) {
                key = "ai:" + resolveUserIdOrIp(request);
                limit = aiLimit;
            } else {
                key = "general:" + resolveUserIdOrIp(request);
                limit = generalLimit;
            }

            Bucket bucket = proxyManager.builder().build(key.getBytes(StandardCharsets.UTF_8), getBucketConfiguration(limit));

            if (!bucket.tryConsume(1)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader("Retry-After", "60");
                response.getWriter().write("Too many requests");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }

    private String resolveUserIdOrIp(HttpServletRequest request) {
        String jwt = getJwtFromRequest(request);
        if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
            try {
                return tokenProvider.extractUserId(jwt).toString();
            } catch (Exception e) {
                // Ignore
            }
        }
        return getClientIP(request);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private Supplier<BucketConfiguration> getBucketConfiguration(int limit) {
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limit)
                        .refillGreedy(limit, Duration.ofMinutes(1))
                        .build())
                .build();
    }
}
