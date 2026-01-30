package com.clienthub.application.aop;

import com.clienthub.common.context.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Aspect to enable Hibernate tenant filter for multi-tenant data isolation.
 */
@Aspect
@Component
public class TenantAspect {

    private static final Logger log = LoggerFactory.getLogger(TenantAspect.class);
    private static final String TENANT_FILTER_NAME = "tenantFilter";

    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(* org.springframework.data.jpa.repository.JpaRepository+.*(..))")
    public void enableTenantFilter() {
        String tenantId = TenantContext.getTenantId();

        // 1. Fail-Secure Validation
        if (tenantId == null || tenantId.isBlank()) {
            log.error("SECURITY ALERT: Accessing DB without TenantID via Repository!");
            throw new SecurityException("Tenant context missing. Access denied.");
        }

        try {
            Session session = entityManager.unwrap(Session.class);

            // 2. Enable or Update Filter
            Filter filter = session.getEnabledFilter(TENANT_FILTER_NAME);
            
            if (filter == null) {
                // First time: enable filter
                filter = session.enableFilter(TENANT_FILTER_NAME);
                log.trace("Enabled tenant filter for tenantId: {}", tenantId);
            }
            
            filter.setParameter("tenantId", tenantId);
            log.trace("Set tenant filter parameter: {}", tenantId);

            // 3. Register Cleanup (Only if in Transaction)
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                registerTransactionCallback(session, tenantId);
            } else {
                // Non-transactional (e.g. simple findById with auto-commit or OSIV)
                // Filter stays active until Session closes (end of request or immediate close)
                log.debug("Filter enabled outside explicit transaction for tenantId: {}", tenantId);
            }

        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            log.error("CRITICAL: Failed to enable tenant filter", e);
            throw new SecurityException("Data isolation error", e);
        }
    }

    private void registerTransactionCallback(Session session, String tenantId) {
        // Register synchronization to disable filter after transaction commit/rollback
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                try {
                    if (session.isOpen()) {
                        // Check if filter is still enabled before disabling (avoid exception)
                        if (session.getEnabledFilter(TENANT_FILTER_NAME) != null) {
                            session.disableFilter(TENANT_FILTER_NAME);
                            log.trace("Disabled tenant filter for tenantId: {} (Status: {})",
                                    tenantId, status == STATUS_COMMITTED ? "COMMITTED" : "ROLLED_BACK");
                        }
                    }
                } catch (Exception e) {
                    // Log but don't disrupt the flow
                    log.warn("Warning: Error during tenant filter cleanup", e);
                }
            }
        });
    }
}