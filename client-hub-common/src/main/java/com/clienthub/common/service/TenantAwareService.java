package com.clienthub.common.service;

import com.clienthub.common.context.TenantContext;
import org.springframework.stereotype.Service;


@Service
public abstract class TenantAwareService {

    /**
     * Retrieves the current tenant ID from ThreadLocal context.
     * 
     * @return The current tenant ID
     * @throws SecurityException if tenant context is missing or empty
     */
    protected String getCurrentTenantId() {
        String tenantId = TenantContext.getTenantId();
        
        if (tenantId == null || tenantId.isBlank()) {
            throw new SecurityException(
                "SECURITY ALERT: Accessing tenant-specific data without tenant context. " +
                "This indicates a breach in multi-tenant isolation."
            );
        }
        
        return tenantId;
    }

    /**
     * Validates that the given tenant ID matches the current tenant context.
     * Used for cross-tenant operation validation.
     * 
     * @param requestedTenantId The tenant ID being accessed
     * @throws SecurityException if tenant IDs don't match
     */
    protected void validateTenantAccess(String requestedTenantId) {
        String currentTenantId = getCurrentTenantId();
        
        if (!currentTenantId.equals(requestedTenantId)) {
            throw new SecurityException(
                String.format(
                    "SECURITY ALERT: Attempted to access data from tenant '%s' while authenticated as tenant '%s'",
                    requestedTenantId, currentTenantId
                )
            );
        }
    }
}
