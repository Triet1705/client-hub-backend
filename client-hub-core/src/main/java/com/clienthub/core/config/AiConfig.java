package com.clienthub.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AiConfig {
    @Value("${ai.ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    @Bean
    public RestClient ollamaRestClient() {
        return RestClient.builder()
                .baseUrl(ollamaUrl)
                .build();
    }
}
