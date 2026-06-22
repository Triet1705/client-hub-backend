package com.clienthub.web.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProductionReadinessConfigTest {

    @Test
    void runRejectsDevelopmentDefaults() {
        ProductionReadinessConfig config = new ProductionReadinessConfig();
        setRequiredFields(config);
        ReflectionTestUtils.setField(config, "datasourcePassword", "postgres");

        assertThrows(IllegalStateException.class, () -> config.run(null));
    }

    @Test
    void runAcceptsExplicitProductionValuesWhenBlockchainDisabled() {
        ProductionReadinessConfig config = new ProductionReadinessConfig();
        setRequiredFields(config);

        assertDoesNotThrow(() -> config.run(null));
    }

    @Test
    void runRequiresBlockchainSettingsWhenBlockchainEnabled() {
        ProductionReadinessConfig config = new ProductionReadinessConfig();
        setRequiredFields(config);
        ReflectionTestUtils.setField(config, "blockchainEnabled", true);
        ReflectionTestUtils.setField(config, "blockchainContractAddress", "");

        assertThrows(IllegalStateException.class, () -> config.run(null));
    }

    private static void setRequiredFields(ProductionReadinessConfig config) {
        ReflectionTestUtils.setField(config, "jwtSecret", "production-secret-value-at-least-32-chars");
        ReflectionTestUtils.setField(config, "datasourcePassword", "db-secret");
        ReflectionTestUtils.setField(config, "minioAccessKey", "minio-user");
        ReflectionTestUtils.setField(config, "minioSecretKey", "minio-secret");
        ReflectionTestUtils.setField(config, "redisPassword", "redis-secret");
        ReflectionTestUtils.setField(config, "allowedOrigins", "https://clienthub.example.com");
        ReflectionTestUtils.setField(config, "blockchainEnabled", false);
        ReflectionTestUtils.setField(config, "blockchainNodeUrl", "");
        ReflectionTestUtils.setField(config, "blockchainAdminPrivateKey", "");
        ReflectionTestUtils.setField(config, "blockchainContractAddress", "");
    }
}
