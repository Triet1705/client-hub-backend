package com.clienthub.web.integration;

import com.clienthub.common.context.TenantContext;
import com.clienthub.domain.entity.Invoice;
import com.clienthub.domain.entity.Project;
import com.clienthub.domain.entity.Task;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.InvoiceStatus;
import com.clienthub.domain.enums.PaymentMethod;
import com.clienthub.domain.enums.ProjectStatus;
import com.clienthub.domain.enums.Role;
import com.clienthub.domain.enums.TaskPriority;
import com.clienthub.domain.enums.TaskStatus;
import com.clienthub.domain.repository.InvoiceRepository;
import com.clienthub.domain.repository.ProjectRepository;
import com.clienthub.domain.repository.TaskRepository;
import com.clienthub.domain.repository.UserRepository;
import com.clienthub.infrastructure.security.CustomUserDetails;
import com.clienthub.web.ClientHubBackendApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ClientHubBackendApplication.class)
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Phase3aTaskPostgresIntegrationTest {

    private static final String TENANT_A = "phase3a-task-a";
    private static final String TENANT_B = "phase3a-task-b";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;
    private User otherClient;
    private User assignee;
    private User otherFreelancer;
    private User administrator;
    private User foreignAdministrator;
    private Project ownedProject;
    private Project otherProject;
    private Task ownedTodo;
    private Task otherClientTask;
    private Invoice conventionalInvoice;
    private Invoice cryptoInvoice;

    @BeforeEach
    void setUp() {
        cleanFixtureData();

        TenantContext.setTenantId(TENANT_A);
        owner = createUser(TENANT_A, "task-owner@a.test", Role.CLIENT);
        otherClient = createUser(TENANT_A, "task-other-client@a.test", Role.CLIENT);
        assignee = createUser(TENANT_A, "task-assignee@a.test", Role.FREELANCER);
        otherFreelancer = createUser(TENANT_A, "task-other-freelancer@a.test", Role.FREELANCER);
        administrator = createUser(TENANT_A, "task-admin@a.test", Role.ADMIN);
        ownedProject = createProject(TENANT_A, "Owned Project", owner);
        otherProject = createProject(TENANT_A, "Other Project", otherClient);
        ownedTodo = createTask(
                TENANT_A, "A owned high todo", ownedProject, assignee,
                TaskStatus.TODO, TaskPriority.HIGH);
        createTask(
                TENANT_A, "B owned low progress", ownedProject, otherFreelancer,
                TaskStatus.IN_PROGRESS, TaskPriority.LOW);
        otherClientTask = createTask(
                TENANT_A, "C other client done", otherProject, otherFreelancer,
                TaskStatus.DONE, TaskPriority.MEDIUM);
        conventionalInvoice = createInvoice(
                "Conventional invoice", PaymentMethod.FIAT, InvoiceStatus.DRAFT);
        cryptoInvoice = createInvoice(
                "Crypto invoice", PaymentMethod.CRYPTO_ESCROW,
                InvoiceStatus.CRYPTO_ESCROW_WAITING);

        TenantContext.setTenantId(TENANT_B);
        User foreignOwner = createUser(TENANT_B, "task-owner@b.test", Role.CLIENT);
        foreignAdministrator = createUser(TENANT_B, "task-admin@b.test", Role.ADMIN);
        Project foreignProject = createProject(TENANT_B, "Foreign Project", foreignOwner);
        createTask(
                TENANT_B, "Foreign task", foreignProject, null,
                TaskStatus.TODO, TaskPriority.HIGH);

        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        cleanFixtureData();
    }

    @Test
    @DisplayName("DEFECT-TASK-01: omitted and combined nullable filters execute on PostgreSQL")
    void administratorFilters_ShouldRemainTypedScopedAndBounded() throws Exception {
        performTasks(administrator, TENANT_A)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[*].title", not(hasItem("Foreign task"))));

        performTasks(
                administrator, TENANT_A, "projectId", ownedProject.getId().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));

        performTasks(administrator, TENANT_A, "status", "TODO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        performTasks(administrator, TENANT_A, "priority", "HIGH")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        performTasks(
                administrator, TENANT_A, "assignedToId", assignee.getId().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        performTasks(
                administrator,
                TENANT_A,
                "projectId", ownedProject.getId().toString(),
                "status", "TODO",
                "priority", "HIGH",
                "assignedToId", assignee.getId().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("A owned high todo"));

        performTasks(administrator, TENANT_A, "size", "1", "sort", "title,asc")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[*].title", contains("A owned high todo")));
    }

    @Test
    @DisplayName("TASK-04/DASH-02: list and summary share actor-scoped populations")
    void actorScopesAndSummaries_ShouldReconcileWithoutPrivilegeWidening() throws Exception {
        performTasks(owner, TENANT_A)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        performSummary(owner, TENANT_A)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));

        performTasks(
                assignee, TENANT_A, "assignedToId", otherFreelancer.getId().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("A owned high todo"));
        performSummary(assignee, TENANT_A)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        performTasks(otherClient, TENANT_A)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("C other client done"));
        performSummary(otherClient, TENANT_A)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        performTasks(foreignAdministrator, TENANT_B)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Foreign task"));
        performSummary(foreignAdministrator, TENANT_B)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("TASK-04: same-tenant and cross-tenant task details preserve denial policy")
    void unauthorizedTaskDetails_ShouldRemainDeniedOrNonDisclosing() throws Exception {
        performTaskDetail(owner, TENANT_A, otherClientTask)
                .andExpect(status().isForbidden());
        performTaskDetail(otherFreelancer, TENANT_A, ownedTodo)
                .andExpect(status().isForbidden());
        performTaskDetail(foreignAdministrator, TENANT_B, ownedTodo)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TASK-02/INV-04/INV-06: known invalid state operations map to controlled 409")
    void invalidStateOperations_ShouldReturnControlledClientErrors() throws Exception {
        mockMvc.perform(authenticated(
                        patch("/api/tasks/{id}/status", ownedTodo.getId())
                                .param("status", "DONE"),
                        owner,
                        TENANT_A))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Invalid State Transition"));

        mockMvc.perform(authenticated(
                        patch("/api/invoices/{id}/status", conventionalInvoice.getId())
                                .param("status", "PAID"),
                        owner,
                        TENANT_A))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Invalid State Transition"));

        mockMvc.perform(authenticated(
                        patch("/api/invoices/{id}/status", cryptoInvoice.getId())
                                .param("status", "PAID"),
                        owner,
                        TENANT_A))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Invalid State Transition"));
    }

    private org.springframework.test.web.servlet.ResultActions performTasks(
            User actor,
            String tenantId,
            String... parameters
    ) throws Exception {
        MockHttpServletRequestBuilder request = authenticated(
                get("/api/tasks"), actor, tenantId);
        for (int index = 0; index < parameters.length; index += 2) {
            request.param(parameters[index], parameters[index + 1]);
        }
        return mockMvc.perform(request);
    }

    private org.springframework.test.web.servlet.ResultActions performSummary(
            User actor,
            String tenantId
    ) throws Exception {
        return mockMvc.perform(authenticated(get("/api/tasks/summary"), actor, tenantId));
    }

    private org.springframework.test.web.servlet.ResultActions performTaskDetail(
            User actor,
            String tenantId,
            Task task
    ) throws Exception {
        return mockMvc.perform(authenticated(
                get("/api/tasks/{id}", task.getId()), actor, tenantId));
    }

    private MockHttpServletRequestBuilder authenticated(
            MockHttpServletRequestBuilder request,
            User actor,
            String tenantId
    ) {
        CustomUserDetails userDetails = CustomUserDetails.build(actor);
        var auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        return request.header("X-Tenant-ID", tenantId).with(authentication(auth));
    }

    private User createUser(String tenantId, String email, Role role) {
        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(email);
        user.setPassword("hashed-password");
        user.setFullName(email);
        user.setRole(role);
        user.setActive(true);
        return userRepository.saveAndFlush(user);
    }

    private Project createProject(String tenantId, String title, User projectOwner) {
        Project project = new Project();
        project.setTenantId(tenantId);
        project.setTitle(title);
        project.setDescription("Test fixture");
        project.setBudget(BigDecimal.TEN);
        project.setStatus(ProjectStatus.IN_PROGRESS);
        project.setOwner(projectOwner);
        return projectRepository.saveAndFlush(project);
    }

    private Task createTask(String tenantId,
                            String title,
                            Project project,
                            User assignedTo,
                            TaskStatus status,
                            TaskPriority priority) {
        Task task = new Task();
        task.setTenantId(tenantId);
        task.setTitle(title);
        task.setDescription("Test fixture");
        task.setProject(project);
        task.setAssignedTo(assignedTo);
        task.setStatus(status);
        task.setPriority(priority);
        task.setEstimatedHours(1);
        return taskRepository.saveAndFlush(task);
    }

    private Invoice createInvoice(
            String title,
            PaymentMethod paymentMethod,
            InvoiceStatus status
    ) {
        Invoice invoice = new Invoice();
        invoice.setTenantId(TENANT_A);
        invoice.setTitle(title);
        invoice.setAmount(BigInteger.TEN);
        invoice.setDueDate(LocalDate.now().plusDays(7));
        invoice.setStatus(status);
        invoice.setProject(ownedProject);
        invoice.setClient(owner);
        invoice.setFreelancer(assignee);
        invoice.setPaymentMethod(paymentMethod);
        return invoiceRepository.saveAndFlush(invoice);
    }

    private void cleanFixtureData() {
        jdbcTemplate.update("DELETE FROM invoices WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
        jdbcTemplate.update("DELETE FROM tasks WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
        jdbcTemplate.update("DELETE FROM projects WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
        jdbcTemplate.update("DELETE FROM users WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
    }
}
