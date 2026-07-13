package com.clienthub.web3.service;

import com.clienthub.common.context.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuditAnchorJob {
    private static final Logger log = LoggerFactory.getLogger(AuditAnchorJob.class);
    private final AuditAnchorService service;

    @Value("${audit.anchor.enabled:false}") private boolean enabled;

    public AuditAnchorJob(AuditAnchorService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${audit.anchor.scheduler_delay_ms:300000}")
    public void process() {
        if (!enabled) return;
        TenantContext.setSystemContext();
        try {
            service.reconcileSubmitted();
            service.submitPending();
            service.run(false);
        } catch (Exception e) {
            log.error("Audit anchoring scheduler failed", e);
        } finally {
            TenantContext.clear();
        }
    }
}
