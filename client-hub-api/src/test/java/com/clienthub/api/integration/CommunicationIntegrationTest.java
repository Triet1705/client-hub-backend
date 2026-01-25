package com.clienthub.api.integration;

import com.clienthub.api.ClientHubBackendApplication;
import com.clienthub.common.context.TenantContext;
import com.clienthub.core.domain.entity.*;
import com.clienthub.core.domain.enums.*;
import com.clienthub.core.dto.communication.CommentRequest;
import com.clienthub.core.repository.*;
import com.clienthub.core.security.CustomUserDetails;
import com.clienthub.core.service.CommunicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ClientHubBackendApplication.class)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")  // Use test profile with H2
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class CommunicationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private CommunicationThreadRepository threadRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    
    @Autowired private jakarta.persistence.EntityManager entityManager;

    private static final String TENANT_ID = "tenant-test";
    private User client;
    private User freelancer;
    private Project project;
    private Task task;

    @BeforeEach
    void setup() {
        TenantContext.setTenantId(TENANT_ID);

        client = createUser("client@test.com", "Client User", Role.CLIENT);
        freelancer = createUser("freelancer@test.com", "Freelancer User", Role.FREELANCER);

        project = createProject("Web3 App", client);
        task = createTask(project, "Implement Smart Contract", freelancer); // Assign to freelancer
    }

    @AfterEach
    void tearDown() {
        TenantContext.setTenantId(TENANT_ID);

        // Sử dụng JdbcTestUtils hoặc Native Query để xóa vật lý (Bypass Soft Delete)
        // Thứ tự xóa quan trọng: Child -> Parent

        // 1. Xóa Notifications (Không có Soft delete, nhưng phụ thuộc User)
        JdbcTestUtils.deleteFromTables(jdbcTemplate, "notifications");

        // 2. Xóa Comments (Có Soft delete -> Phải dùng JDBC để xóa vật lý)
        JdbcTestUtils.deleteFromTables(jdbcTemplate, "comments");

        // 3. Xóa Threads (Có Soft delete -> Phải dùng JDBC)
        JdbcTestUtils.deleteFromTables(jdbcTemplate, "communication_threads");

        // 4. Xóa Tasks & Projects (Có Soft delete)
        JdbcTestUtils.deleteFromTables(jdbcTemplate, "tasks", "projects");

        // 5. Xóa Users (Parent cao nhất)
        JdbcTestUtils.deleteFromTables(jdbcTemplate, "users");

        TenantContext.clear();
    }

    @Test
    @DisplayName("Flow: Client posts comment on Task -> Thread created -> Notification sent to Freelancer")
    void testFullCommunicationFlow() throws Exception {
        CommentRequest request = new CommentRequest();
        request.setTargetType(CommentTargetType.TASK);
        request.setTargetId(task.getId().toString());
        request.setContent("Please prioritize gas optimization.");

        // Use .with(authentication(...)) to inject CustomUserDetails
        CustomUserDetails userDetails = CustomUserDetails.build(client);
        UsernamePasswordAuthenticationToken auth = 
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        mockMvc.perform(post("/api/comments")
                        .header("X-Tenant-ID", TENANT_ID)
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content", is("Please prioritize gas optimization.")))
                .andExpect(jsonPath("$.author.email", is(client.getEmail())));

        // Re-set tenant context after MockMvc request (it gets cleared after response)
        TenantContext.setTenantId(TENANT_ID);
        
        List<CommunicationThread> threads = threadRepository.findAll();
        assertEquals(1, threads.size());
        CommunicationThread thread = threads.get(0);
        assertEquals(CommentTargetType.TASK, thread.getTargetType());
        assertEquals(task.getId().toString(), thread.getTargetId());

        List<Comment> comments = commentRepository.findAll();
        assertEquals(1, comments.size());
        assertEquals(thread.getId(), comments.get(0).getThread().getId());

        List<Notification> notifications = notificationRepository.findAll();
        assertEquals(1, notifications.size());
        Notification notif = notifications.get(0);

        assertEquals(freelancer.getId(), notif.getRecipient().getId(), "Recipient must be freelancer");
        assertEquals(NotificationType.NEW_COMMENT, notif.getType());
        assertEquals(false, notif.isRead());
    }

    @Test
    @DisplayName("Security: Freelancer can access assigned task comments")
    void testAccessControl_FreelancerAccess() throws Exception {
        TenantContext.setTenantId(TENANT_ID);  // Ensure tenant context is set
        
        // Freelancer is assigned to task, should have access
        CustomUserDetails userDetails = CustomUserDetails.build(freelancer);
        UsernamePasswordAuthenticationToken auth = 
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        mockMvc.perform(get("/api/comments")
                        .header("X-Tenant-ID", TENANT_ID)
                        .with(authentication(auth))
                        .param("targetType", "TASK")
                        .param("targetId", task.getId().toString()))
                .andExpect(status().isOk());  // 200 OK - has access
    }

    private void authenticateUser(User user) {
        CustomUserDetails userDetails = CustomUserDetails.build(user);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
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

    private Project createProject(String title, User owner) {
        Project p = new Project();
        p.setTenantId(TENANT_ID);
        p.setTitle(title);
        p.setStatus(ProjectStatus.IN_PROGRESS);
        p.setOwner(owner);
        p.setBudget(java.math.BigDecimal.TEN);
        return projectRepository.save(p);
    }

    private Task createTask(Project project, String title, User assignee) {
        Task t = new Task();
        t.setTenantId(TENANT_ID);
        t.setTitle(title);
        t.setProject(project);
        t.setAssignedTo(assignee);
        t.setStatus(TaskStatus.IN_PROGRESS);
        t.setPriority(TaskPriority.HIGH);
        t.setEstimatedHours(10);
        return taskRepository.save(t);
    }
}