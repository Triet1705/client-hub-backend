package com.clienthub.infrastructure.persistence;

import com.clienthub.common.context.TenantContext;
import jakarta.persistence.EntityManager;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Aspect
@Component
@ConditionalOnProperty(name = "app.rls.enabled", havingValue = "true", matchIfMissing = true)
public class TenantConnectionAspect {

    private static final Logger log = LoggerFactory.getLogger(TenantConnectionAspect.class);
    private static final String SAFE_TENANT_PATTERN = "^[a-zA-Z0-9_-]+$";

    private final EntityManager entityManager;

    public TenantConnectionAspect(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Before("@annotation(org.springframework.transaction.annotation.Transactional) || @within(org.springframework.transaction.annotation.Transactional)")
    public void setTenantId() {
        String tenantId = TenantContext.getTenantId();
        if (tenantId != null && !tenantId.isEmpty()) {
            if (!tenantId.matches(SAFE_TENANT_PATTERN)) {
                throw new IllegalStateException("Invalid tenant ID in TenantContext");
            }
            log.debug("Setting app.current_tenant to {}", tenantId);
            entityManager.createNativeQuery("SELECT set_config('app.current_tenant', :tenantId, true)")
                    .setParameter("tenantId", tenantId)
                    .getSingleResult();
        } else {
            // For requests without tenant (e.g. system tasks or public endpoints), clear it
            log.debug("Clearing app.current_tenant");
            entityManager.createNativeQuery("SELECT set_config('app.current_tenant', '', true)")
                    .getSingleResult();
        }
    }
}
