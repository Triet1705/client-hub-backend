package com.clienthub.api.config;

import com.clienthub.common.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TenantInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(TenantInterceptor.class);
    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String DEFAULT_TENANT = "default";

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler
    ) {
        try {
            String tenantId = extractTenantId(request);
            TenantContext.setTenantId(tenantId);
            log.debug("Tenant context set: {}", tenantId);
        } catch (Exception e) {
            log.error("Error setting tenant context", e);
            TenantContext.setTenantId(DEFAULT_TENANT);
        }
        return true;
    }

    @Override
    public void afterCompletion(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler,
            Exception ex
    ) {
        // Always cleanup to prevent ThreadLocal leaks
        TenantContext.clear();
        if (ex != null) {
            log.error("Request completed with exception", ex);
        }
    }

    private String extractTenantId(HttpServletRequest request) {
        String tenantId = request.getHeader(TENANT_HEADER);
        
        if (tenantId == null || tenantId.isBlank()) {
            // TODO: In production, consider rejecting requests without tenant ID
            log.warn("No tenant ID in request header, using default");
            return DEFAULT_TENANT;
        }
        
        // Validate tenant ID format (alphanumeric, dash, underscore only)
        if (!tenantId.matches("^[a-zA-Z0-9_-]+$")) {
            log.warn("Invalid tenant ID format: {}", tenantId);
            return DEFAULT_TENANT;
        }
        
        return tenantId;
    }
}
