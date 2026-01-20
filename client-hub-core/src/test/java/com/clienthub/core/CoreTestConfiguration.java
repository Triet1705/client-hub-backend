package com.clienthub.core;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableJpaRepositories(basePackages = "com.clienthub.core.repository")
@EntityScan(basePackages = "com.clienthub.core.domain.entity")
@EnableJpaAuditing
@ComponentScan(
    basePackages = "com.clienthub.common.context",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.clienthub\\.core\\.(aop|service).*"
    )
)
public class CoreTestConfiguration {
    // Exclude AOP and Service layers for @DataJpaTest
    // Repository tests don't need audit logging
}