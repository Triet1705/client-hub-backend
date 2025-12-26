package com.clienthub.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "com.clienthub.api",
    "com.clienthub.core",
    "com.clienthub.web3",
    "com.clienthub.common"
})
public class ClientHubBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientHubBackendApplication.class, args);
    }
}
