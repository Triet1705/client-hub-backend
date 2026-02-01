package com.clienthub.domain.repository;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableJpaRepositories(basePackages = "com.clienthub.domain.repository")
@EntityScan(basePackages = "com.clienthub.domain.entity")
@EnableJpaAuditing
@EnableAspectJAutoProxy
@ComponentScan(basePackages = {
    "com.clienthub.domain.aop",
    "com.clienthub.common.context"
})
public class TestJpaConfig {
}
