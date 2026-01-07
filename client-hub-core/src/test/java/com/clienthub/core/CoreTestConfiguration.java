package com.clienthub.core;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableJpaRepositories(basePackages = "com.clienthub.core.repository")
@EntityScan(basePackages = "com.clienthub.core.domain.entity")
@EnableJpaAuditing
@EnableAspectJAutoProxy
@ComponentScan(basePackages = {
    "com.clienthub.core.aop",
    "com.clienthub.common.context"
})
public class CoreTestConfiguration {
}