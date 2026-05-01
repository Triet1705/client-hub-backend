package com.clienthub.web.integration;

import com.clienthub.web.ClientHubBackendApplication;
import com.clienthub.common.context.TenantContext;
import com.clienthub.domain.entity.Notification;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.NotificationType;
import com.clienthub.domain.enums.Role;
import com.clienthub.domain.repository.NotificationRepository;
import com.clienthub.domain.repository.UserRepository;
import com.clienthub.infrastructure.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ClientHubBackendApplication.class)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String TENANT_ID = "tenant-test";

    private User recipient;
    private User otherUser;

    @BeforeEach
    void setup() {
        TenantContext.setTenantId(TENANT_ID);

        recipient = createUser("recipient@test.com", "Recipient User", Role.CLIENT);
        otherUser = createUser("other@test.com", "Other User", Role.FREELANCER);

        createNotification(recipient, "Unread #1", false, "TASK", "task-1");
        createNotification(recipient, "Read #1", true, "TASK", "task-2");
        createNotification(otherUser, "Other user notification", false, "TASK", "task-3");
    }

    @AfterEach
    void tearDown() {
        TenantContext.setTenantId(TENANT_ID);
        JdbcTestUtils.deleteFromTables(jdbcTemplate, "notifications");
        JdbcTestUtils.deleteFromTables(jdbcTemplate, "users");
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET /api/notifications returns only current user notifications")
    void getNotifications_returnsRecipientScopedPage() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .header("X-Tenant-ID", TENANT_ID)
                        .with(authentication(authFor(recipient))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].message", is("Read #1")))
                .andExpect(jsonPath("$.content[1].message", is("Unread #1")));
    }

    @Test
    @DisplayName("GET /api/notifications with unreadOnly returns unread notifications only")
    void getNotifications_unreadOnly_returnsUnreadItems() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .param("unreadOnly", "true")
                        .header("X-Tenant-ID", TENANT_ID)
                        .with(authentication(authFor(recipient))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].message", is("Unread #1")))
                .andExpect(jsonPath("$.content[0].read", is(false)));
    }

    @Test
    @DisplayName("GET /api/notifications/unread-count returns unread count for current user")
    void getUnreadCount_returnsCorrectValue() throws Exception {
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("X-Tenant-ID", TENANT_ID)
                        .with(authentication(authFor(recipient))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount", is(1)));
    }

    @Test
    @DisplayName("PATCH /api/notifications/{id}/read marks target notification as read")
    void markAsRead_marksNotificationForRecipient() throws Exception {
        Notification unread = notificationRepository
                .findByRecipientIdAndTenantIdOrderByCreatedAtDesc(recipient.getId(), TENANT_ID,
                        org.springframework.data.domain.PageRequest.of(0, 10))
                .stream()
                .filter(n -> !n.isRead())
                .findFirst()
                .orElseThrow();

        mockMvc.perform(patch("/api/notifications/{id}/read", unread.getId())
                        .header("X-Tenant-ID", TENANT_ID)
                        .with(authentication(authFor(recipient))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(unread.getId().intValue())))
                .andExpect(jsonPath("$.read", is(true)));
    }

    @Test
    @DisplayName("PATCH /api/notifications/{id}/read returns 404 for notification of another user")
    void markAsRead_returns404WhenNotificationDoesNotBelongToCurrentUser() throws Exception {
        Notification othersNotification = notificationRepository
                .findByRecipientIdAndTenantIdOrderByCreatedAtDesc(otherUser.getId(), TENANT_ID,
                        org.springframework.data.domain.PageRequest.of(0, 10))
                .stream()
                .findFirst()
                .orElseThrow();

        mockMvc.perform(patch("/api/notifications/{id}/read", othersNotification.getId())
                        .header("X-Tenant-ID", TENANT_ID)
                        .with(authentication(authFor(recipient))))
                .andExpect(status().isNotFound());
    }

            @Test
            @DisplayName("PATCH /api/notifications/read-all marks all unread notifications for current user")
            void markAllAsRead_marksAllUnreadForCurrentUser() throws Exception {
            mockMvc.perform(patch("/api/notifications/read-all")
                    .header("X-Tenant-ID", TENANT_ID)
                    .with(authentication(authFor(recipient))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount", is(1)));

            mockMvc.perform(get("/api/notifications/unread-count")
                    .header("X-Tenant-ID", TENANT_ID)
                    .with(authentication(authFor(recipient))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount", is(0)));

            mockMvc.perform(get("/api/notifications/unread-count")
                    .header("X-Tenant-ID", TENANT_ID)
                    .with(authentication(authFor(otherUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount", is(1)));
            }

    private User createUser(String email, String name, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("hashedPwd");
        user.setFullName(name);
        user.setRole(role);
        user.setTenantId(TENANT_ID);
        return userRepository.save(user);
    }

    private void createNotification(User recipientUser, String message, boolean isRead, String referenceType,
                                    String referenceId) {
        Notification notification = new Notification();
        notification.setTenantId(TENANT_ID);
        notification.setRecipient(recipientUser);
        notification.setType(NotificationType.NEW_COMMENT);
        notification.setReferenceType(referenceType);
        notification.setReferenceId(referenceId);
        notification.setMessage(message);
        notification.setRead(isRead);
        notificationRepository.save(notification);
    }

    private UsernamePasswordAuthenticationToken authFor(User user) {
        CustomUserDetails userDetails = CustomUserDetails.build(user);
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}
