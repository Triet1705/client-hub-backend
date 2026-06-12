package com.clienthub.infrastructure.persistence;

import com.clienthub.common.context.TenantContext;
import jakarta.persistence.EntityManager;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TenantConnectionAspect {

    private static final Logger log = LoggerFactory.getLogger(TenantConnectionAspect.class);

    private final EntityManager entityManager;

    public TenantConnectionAspect(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Before("@annotation(org.springframework.transaction.annotation.Transactional) || @within(org.springframework.transaction.annotation.Transactional)")
    public void setTenantId() {
        String tenantId = TenantContext.getTenantId();
        if (tenantId != null && !tenantId.isEmpty()) {
            log.debug("Setting app.current_tenant to {}", tenantId);
            entityManager.createNativeQuery("SET LOCAL app.current_tenant = '" + tenantId + "'").executeUpdate();
        } else {
            // For requests without tenant (e.g. system tasks or public endpoints), clear it
            log.debug("Clearing app.current_tenant");
            entityManager.createNativeQuery("SET LOCAL app.current_tenant = ''").executeUpdate();
        }
    }
}
