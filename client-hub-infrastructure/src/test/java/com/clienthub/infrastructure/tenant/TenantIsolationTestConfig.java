package com.clienthub.infrastructure.tenant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@TestConfiguration
@EnableAspectJAutoProxy
@EnableJpaRepositories(basePackages = "com.clienthub.domain.repository")
@EntityScan(basePackages = "com.clienthub.domain.entity")
@ComponentScan(
    basePackages = {
        "com.clienthub.common.context",
        "com.clienthub.core.aop",
        "com.clienthub.domain.aop"  
    },
    excludeFilters = {
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.clienthub\\.core\\.aop\\.AuditAspect"
        ),
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.clienthub\\.core\\.service.*"
        )
    }
)
public class TenantIsolationTestConfig {
}
