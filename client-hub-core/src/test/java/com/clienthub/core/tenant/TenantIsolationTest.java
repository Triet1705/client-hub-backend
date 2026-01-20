package com.clienthub.core.tenant;

import com.clienthub.common.context.TenantContext;
import com.clienthub.core.domain.entity.User;
import com.clienthub.core.domain.enums.Role;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(TenantIsolationTestConfig.class)
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

    private User createUser(String tenantId, String email, Role role) {
        TenantContext.setTenantId(tenantId);
        User user = new User();
        user.setEmail(email);
        user.setPassword("password");
        user.setFullName("User " + email);
        user.setRole(role);
        user.setTenantId(tenantId);
        userRepository.save(user);
        return user;
    }

    @Test
    @DisplayName("CHDEV-69: Custom JPQL queries respect tenant filter")
    void testCustomQueriesRespectFilter() {
        createUser("TENANT_1", "user1@t1.com", Role.CLIENT);
        User user2 = createUser("TENANT_2", "user2@t2.com", Role.CLIENT);

        entityManager.flush();
        entityManager.clear();
        TenantContext.clear();

        TenantContext.setTenantId("TENANT_1");

        Optional<User> result = userRepository.findByEmailCustom("user2@t2.com");

        assertTrue(result.isEmpty(), "Custom JPQL must NOT find user from another tenant");
    }

    @Test
    @DisplayName("CHDEV-69: Pagination respects tenant isolation")
    void testPaginationRespectFilter() {
        IntStream.range(0, 15).forEach(i -> createUser("TENANT_1", "t1_user" + i + "@test.com", Role.CLIENT));
        IntStream.range(0, 10).forEach(i -> createUser("TENANT_2", "t2_user" + i + "@test.com", Role.CLIENT));

        entityManager.flush();
        entityManager.clear();
        TenantContext.clear();

        TenantContext.setTenantId("TENANT_1");

        Page<User> page = userRepository.findAll(PageRequest.of(0, 10));

        assertEquals(15, page.getTotalElements(), "Total elements for Tenant 1 should be 15");
        assertEquals(2, page.getTotalPages(), "Total pages for Tenant 1 should be 2 (15/10)");
        assertEquals(10, page.getContent().size(), "Current page size should be 10");

        assertTrue(page.getContent().stream().allMatch(u -> u.getTenantId().equals("TENANT_1")),
                "All users in page must belong to TENANT_1");
    }

    @Test
    @DisplayName("CHDEV-69: Native queries BYPASS filter (Documented Limitation)")
    void testNativeQueriesBypassFilter() {
        createUser("TENANT_2", "user2@t2.com", Role.CLIENT);

        entityManager.flush();
        entityManager.clear();
        TenantContext.clear();

        TenantContext.setTenantId("TENANT_1");

        List<User> users = userRepository.findAllNative();

        assertFalse(users.isEmpty(), "Native query SHOULD bypass filter (Known Limitation)");
        assertTrue(users.stream().anyMatch(u -> u.getTenantId().equals("TENANT_2")),
                "Should find Tenant 2 data due to native query bypass");
    }

    @Test
    @DisplayName("CHDEV-69: DELETE queries respect tenant filter")
    void testDeleteQueriesRespectFilter() {
        User user1 = createUser("TENANT_1", "user1@t1.com", Role.CLIENT);
        createUser("TENANT_1", "user2@t1.com", Role.CLIENT);
        User user2tenant = createUser("TENANT_2", "user2@t2.com", Role.CLIENT);
        UUID user1Id = user1.getId();
        UUID tenant2UserId = user2tenant.getId();

        entityManager.flush();
        entityManager.clear();
        TenantContext.clear();

        TenantContext.setTenantId("TENANT_1");

        userRepository.deleteById(user1Id);
        entityManager.flush();

        userRepository.deleteById(tenant2UserId); 
        entityManager.flush();

        long countT1 = userRepository.count();
        assertEquals(1, countT1, "Tenant 1 should have 1 user left (deleted own, couldn't delete Tenant 2's)");
    }

    @Test
    @DisplayName("CHDEV-69: Aggregate queries respect tenant filter")
    void testAggregateQueriesRespectFilter() {
        IntStream.range(0, 5).forEach(i -> createUser("TENANT_1", "t1_" + i + "@test.com", Role.CLIENT));
        IntStream.range(0, 3).forEach(i -> createUser("TENANT_2", "t2_" + i + "@test.com", Role.CLIENT));

        entityManager.flush();
        entityManager.clear();
        TenantContext.clear();

        TenantContext.setTenantId("TENANT_1");

        long count = userRepository.countByRole(Role.CLIENT);

        assertEquals(5, count, "Aggregate count should only include Tenant 1 records");
    }

    @Test
    @DisplayName("Security: Missing Tenant Context throws Exception")
    void testMissingTenantContext() {
        TenantContext.clear();
        assertThrows(SecurityException.class, () -> userRepository.findAll());
    }
}