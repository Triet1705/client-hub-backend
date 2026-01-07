package com.clienthub.core.tenant;

import com.clienthub.common.context.TenantContext;
import com.clienthub.core.CoreTestConfiguration;
import com.clienthub.core.domain.entity.User;
import com.clienthub.core.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(CoreTestConfiguration.class)
public class TenantIsolationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private UserRepository userRepository;
    
    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Verify Isolation: Tenant A cannot see data of Tenant B (PostgreSQL)")
    void testTenantIsolation() {
        TenantContext.setTenantId("TENANT_1");

        User user1 = new User();
        user1.setEmail("user1@tenant1.com");
        user1.setPassword("password123");
        user1.setFullName("Tenant One User");
        user1.setTenantId("TENANT_1");
        user1.setRole(User.Role.CLIENT);
        
        userRepository.save(user1);
        UUID user1Id = user1.getId();
        
        entityManager.flush();
        entityManager.clear();
        
        TenantContext.clear();

        TenantContext.setTenantId("TENANT_2");

        User user2 = new User();
        user2.setEmail("user2@tenant2.com");
        user2.setPassword("password456");
        user2.setFullName("Tenant Two User");
        user2.setTenantId("TENANT_2");
        user2.setRole(User.Role.CLIENT);

        userRepository.save(user2);
        UUID user2Id = user2.getId();
        
        entityManager.flush();
        entityManager.clear();
        TenantContext.clear();

        TenantContext.setTenantId("TENANT_1");

        List<User> tenant1Users = userRepository.findAll();
        assertEquals(1, tenant1Users.size(), "Tenant 1 should see exactly 1 user");
        assertEquals("user1@tenant1.com", tenant1Users.get(0).getEmail());
        
        assertEquals(1, userRepository.count(), "Tenant 1 count should be 1 (filter active)");

        entityManager.clear();
        TenantContext.setTenantId("TENANT_2");

        List<User> tenant2Users = userRepository.findAll();
        assertEquals(1, tenant2Users.size(), "Tenant 2 should see exactly 1 user");
        assertEquals("user2@tenant2.com", tenant2Users.get(0).getEmail());
        assertEquals(1, userRepository.count(), "Tenant 2 count should be 1 (filter active)");
    }

    @Test
    @DisplayName("Security: Missing Tenant Context throws Exception")
    void testMissingTenantContext() {
        TenantContext.clear();

        SecurityException exception = assertThrows(SecurityException.class, () -> {
            userRepository.findAll();
        });
        
        assertNotNull(exception.getMessage());
    }
}