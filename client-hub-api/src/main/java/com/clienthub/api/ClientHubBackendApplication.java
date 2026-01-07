package com.clienthub.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
    "com.clienthub.api",
    "com.clienthub.core",
    "com.clienthub.web3",
    "com.clienthub.common"
})
@EnableJpaRepositories(basePackages = "com.clienthub.core.repository")
@EntityScan(basePackages = "com.clienthub.core.domain.entity")
public class ClientHubBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientHubBackendApplication.class, args);
    }
}
