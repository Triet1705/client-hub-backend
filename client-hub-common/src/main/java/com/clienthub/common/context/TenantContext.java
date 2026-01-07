package com.clienthub.common.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TenantContext {
    private static final Logger log = LoggerFactory.getLogger(TenantContext.class);
    private static final ThreadLocal<String> CURRENT_TENANT = ThreadLocal.withInitial(() -> null);

    // Private constructor to prevent instantiation
    private TenantContext() {
        throw new AssertionError("Utility class - do not instantiate");
    }

    public static void setTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            log.warn("Attempted to set null/empty tenant ID. Clearing context to prevent leakage.");
            clear();
            return;
        }
        log.debug("Setting tenantId to: {}", tenantId);
        CURRENT_TENANT.set(tenantId);
    }

    public static String getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        log.debug("Clearing tenant context");
        CURRENT_TENANT.remove();
    }

    public static boolean isSet() {
        return CURRENT_TENANT.get() != null;
    }
}