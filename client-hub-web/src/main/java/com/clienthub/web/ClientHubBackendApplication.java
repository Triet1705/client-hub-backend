package com.clienthub.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
    "com.clienthub.web",
    "com.clienthub.application",
    "com.clienthub.infrastructure",
    "com.clienthub.web3"
})
@EnableJpaRepositories(basePackages = "com.clienthub.domain.repository")
@EntityScan(basePackages = "com.clienthub.domain.entity")
public class ClientHubBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientHubBackendApplication.class, args);
    }
}
