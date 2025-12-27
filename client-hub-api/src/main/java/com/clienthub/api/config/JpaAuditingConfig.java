package com.clienthub.api.config;

import com.clienthub.common.context.TenantContext;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            String tenantId = TenantContext.getTenantId();
            return Optional.ofNullable(tenantId != null ? tenantId : "system");
        };
    }
}
