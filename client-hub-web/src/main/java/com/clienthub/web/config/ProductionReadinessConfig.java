package com.clienthub.web.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionReadinessConfig implements ApplicationRunner {

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Value("${minio.access-key:}")
    private String minioAccessKey;

    @Value("${minio.secret-key:}")
    private String minioSecretKey;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${cors.allowed-origins:}")
    private String allowedOrigins;

    @Value("${app.tenant.require-header:false}")
    private boolean tenantHeaderRequired;

    @Value("${blockchain.enabled:false}")
    private boolean blockchainEnabled;

    @Value("${blockchain.node_url:}")
    private String blockchainNodeUrl;

    @Value("${blockchain.admin_private_key:}")
    private String blockchainAdminPrivateKey;

    @Value("${blockchain.contract_address:}")
    private String blockchainContractAddress;

    @Override
    public void run(ApplicationArguments args) {
        List<String> failures = new ArrayList<>();

        require(jwtSecret.length() >= 32, failures, "jwt.secret must be set to a strong production value");
        rejectDefault(datasourcePassword, "postgres", failures, "spring.datasource.password must not use the dev default");
        rejectDefault(minioAccessKey, "minioadmin", failures, "minio.access-key must not use the dev default");
        rejectDefault(minioSecretKey, "minioadmin", failures, "minio.secret-key must not use the dev default");
        rejectDefault(redisPassword, "redis_password", failures, "spring.data.redis.password must not use the dev default");
        require(!allowedOrigins.isBlank(), failures, "cors.allowed-origins must be explicit in prod");
        require(!allowedOrigins.contains("*"), failures, "cors.allowed-origins must not contain wildcard origins in prod");
        require(!allowedOrigins.contains("localhost") && !allowedOrigins.contains("127.0.0.1"),
                failures,
                "cors.allowed-origins must not contain localhost origins in prod");
        require(tenantHeaderRequired, failures, "app.tenant.require-header must be true in prod");

        if (blockchainEnabled) {
            require(!blockchainNodeUrl.isBlank(), failures, "blockchain.node_url is required when blockchain is enabled");
            require(!blockchainAdminPrivateKey.isBlank(), failures,
                    "blockchain.admin_private_key is required when blockchain is enabled");
            require(!blockchainContractAddress.isBlank(), failures,
                    "blockchain.contract_address is required when blockchain is enabled");
        }

        if (!failures.isEmpty()) {
            throw new IllegalStateException("Production readiness check failed: " + String.join("; ", failures));
        }
    }

    private static void rejectDefault(String value, String forbidden, List<String> failures, String message) {
        require(!value.isBlank() && !forbidden.equals(value), failures, message);
    }

    private static void require(boolean condition, List<String> failures, String message) {
        if (!condition) {
            failures.add(message);
        }
    }
}
