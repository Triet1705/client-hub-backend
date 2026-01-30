package com.clienthub.infrastructure.tenant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.FilterType;

@TestConfiguration
@EnableAspectJAutoProxy
@ComponentScan(
    basePackages = {
        "com.clienthub.common.context",
        "com.clienthub.core.aop"  
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
