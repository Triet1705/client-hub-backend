package com.clienthub.common.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe context holder for tenant ID.
 * Uses ThreadLocal to store tenant information per request thread.
 * 
 * IMPORTANT: Always call clear() in finally block or interceptor afterCompletion
 * to prevent ThreadLocal memory leaks in thread pools.
 */
public final class TenantContext {
    private static final Logger log = LoggerFactory.getLogger(TenantContext.class);
    private static final ThreadLocal<String> currentTenant = ThreadLocal.withInitial(() -> null);

    // Private constructor to prevent instantiation
    private TenantContext() {
        throw new AssertionError("Utility class - do not instantiate");
    }

    public static void setTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            log.warn("Attempted to set null or blank tenant ID");
            return;
        }
        currentTenant.set(tenantId);
    }

    public static String getTenantId() {
        return currentTenant.get();
    }

    /**
     * Clears the tenant context. MUST be called after request processing.
     */
    public static void clear() {
        currentTenant.remove();
    }

    /**
     * Check if tenant context is set
     */
    public static boolean isSet() {
        return currentTenant.get() != null;
    }
}
