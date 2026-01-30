package com.clienthub.domain.repository;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableAutoConfiguration
@EntityScan(basePackages = "com.clienthub.domain.entity")
@EnableJpaRepositories(basePackages = "com.clienthub.domain.repository")
public class TestJpaConfig {
}
