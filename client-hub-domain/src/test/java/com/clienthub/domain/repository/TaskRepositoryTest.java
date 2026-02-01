package com.clienthub.domain.repository;

import com.clienthub.common.context.TenantContext;
import com.clienthub.domain.entity.Project;
import com.clienthub.domain.entity.Task;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.ProjectStatus;
import com.clienthub.domain.enums.Role;
import com.clienthub.domain.enums.TaskPriority;
import com.clienthub.domain.enums.TaskStatus;
import com.clienthub.domain.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TestJpaConfig.class)
class TaskRepositoryTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private static final String TENANT_1 = "tenant-1";
    private static final String TENANT_2 = "tenant-2";

    private Project project1;
    private Project project2;
    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        setupTestData();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void setupTestData() {
        // Create users
        TenantContext.setTenantId(TENANT_1);
        user1 = createUser(TENANT_1, "user1@tenant1.com");

        TenantContext.setTenantId(TENANT_2);
        user2 = createUser(TENANT_2, "user2@tenant2.com");

        // Create projects
        TenantContext.setTenantId(TENANT_1);
        project1 = createProject(TENANT_1, "Project 1", user1);

        TenantContext.setTenantId(TENANT_2);
        project2 = createProject(TENANT_2, "Project 2", user2);

        entityManager.flush();
        entityManager.clear();
        TenantContext.clear();
    }

    @Test
    @DisplayName("CHDEV-303: Should find task by ID only within same tenant")
    void findByIdAndTenantId_ShouldIsolateTenants() {
        // Given: Create task in Tenant 1
        TenantContext.setTenantId(TENANT_1);
        Task task = createTask(project1, "Task in Tenant 1");
        UUID taskId = task.getId();

        entityManager.flush();
        entityManager.clear();
        TenantContext.clear();

        // When: Query from Tenant 1 context
        TenantContext.setTenantId(TENANT_1);
        Optional<Task> found = taskRepository.findByIdAndTenantId(taskId, TENANT_1);

        // Then: Should find the task
        assertTrue(found.isPresent());
        assertEquals("Task in Tenant 1", found.get().getTitle());

        // When: Query same task from Tenant 2 context
        TenantContext.setTenantId(TENANT_2);
        Optional<Task> notFound = taskRepository.findByIdAndTenantId(taskId, TENANT_2);

        // Then: Should NOT find the task (cross-tenant isolation)
        assertTrue(notFound.isEmpty());
    }

    @Test
    @DisplayName("CHDEV-303: Should find tasks by project with tenant isolation")
    void findByProjectIdAndTenantId_ShouldIsolateTenants() {
        // Given: Create 3 tasks in Tenant 1, 2 tasks in Tenant 2
        TenantContext.setTenantId(TENANT_1);
        createTask(project1, "T1 Task 1");
        createTask(project1, "T1 Task 2");
        createTask(project1, "T1 Task 3");

        TenantContext.setTenantId(TENANT_2);
        createTask(project2, "T2 Task 1");
        createTask(project2, "T2 Task 2");

        entityManager.flush();
        entityManager.clear();
        TenantContext.clear();

        // When: Query from Tenant 1
        TenantContext.setTenantId(TENANT_1);
        Page<Task> tenant1Tasks = taskRepository.findByProjectIdAndTenantId(
                project1.getId(), TENANT_1, PageRequest.of(0, 10));

        // Then: Should find only Tenant 1 tasks
        assertEquals(3, tenant1Tasks.getTotalElements());
        assertTrue(tenant1Tasks.getContent().stream()
                .allMatch(t -> t.getTenantId().equals(TENANT_1)));
    }

    @Test
    @DisplayName("CHDEV-303: Should find tasks by assignee with tenant isolation")
    void findByAssignedToIdAndTenantId_ShouldIsolateTenants() {
        // Given: Create tasks assigned to users
        TenantContext.setTenantId(TENANT_1);
        Task task1 = createTask(project1, "Task for User 1");
        task1.setAssignedTo(user1);
        taskRepository.save(task1);

        TenantContext.setTenantId(TENANT_2);
        Task task2 = createTask(project2, "Task for User 2");
        task2.setAssignedTo(user2);
        taskRepository.save(task2);

        entityManager.flush();
        entityManager.clear();
        TenantContext.clear();

        // When: Query tasks for User 1 from Tenant 1
        TenantContext.setTenantId(TENANT_1);
        Page<Task> user1Tasks = taskRepository.findByAssignedToIdAndTenantId(
                user1.getId(), TENANT_1, PageRequest.of(0, 10));

        // Then: Should find only tasks assigned to User 1
        assertEquals(1, user1Tasks.getTotalElements());
        assertEquals("Task for User 1", user1Tasks.getContent().get(0).getTitle());

        // When: Try to query User 2's tasks from Tenant 1 context
        Page<Task> crossTenantTasks = taskRepository.findByAssignedToIdAndTenantId(
                user2.getId(), TENANT_1, PageRequest.of(0, 10));

        // Then: Should find nothing (cross-tenant protection)
        assertEquals(0, crossTenantTasks.getTotalElements());
    }

    @Test
    @DisplayName("CHDEV-303: Should filter tasks by status and tenant")
    void findByProjectIdAndStatusAndTenantId_ShouldWork() {
        // Given: Create tasks with different statuses
        TenantContext.setTenantId(TENANT_1);
        Task todo = createTask(project1, "TODO Task");
        todo.setStatus(TaskStatus.TODO);
        taskRepository.save(todo);

        Task inProgress = createTask(project1, "In Progress Task");
        inProgress.setStatus(TaskStatus.IN_PROGRESS);
        taskRepository.save(inProgress);

        entityManager.flush();
        entityManager.clear();
        TenantContext.clear();

        // When: Query TODO tasks
        TenantContext.setTenantId(TENANT_1);
        Page<Task> todoTasks = taskRepository.findByProjectIdAndStatusAndTenantId(
                project1.getId(), TaskStatus.TODO, TENANT_1, PageRequest.of(0, 10));

        // Then: Should find only TODO tasks
        assertEquals(1, todoTasks.getTotalElements());
        assertEquals(TaskStatus.TODO, todoTasks.getContent().get(0).getStatus());
    }

    @Test
    @DisplayName("CHDEV-303: Should load tasks with relations using FETCH JOIN")
    void findAllByTenantIdWithRelations_ShouldAvoidNPlus1() {
        // Given: Create tasks with project and assignee
        TenantContext.setTenantId(TENANT_1);
        Task task = createTask(project1, "Task with relations");
        task.setAssignedTo(user1);
        taskRepository.save(task);

        entityManager.flush();
        entityManager.clear();
        TenantContext.clear();

        // When: Load tasks with relations
        TenantContext.setTenantId(TENANT_1);
        List<Task> tasks = taskRepository.findAllByTenantIdWithRelations(TENANT_1);

        // Then: Should load all relations in single query
        assertEquals(1, tasks.size());
        Task loadedTask = tasks.get(0);
        
        // Verify relations are loaded (no LazyInitializationException)
        assertNotNull(loadedTask.getProject());
        assertEquals("Project 1", loadedTask.getProject().getTitle());
        assertNotNull(loadedTask.getAssignedTo());
        assertEquals("user1@tenant1.com", loadedTask.getAssignedTo().getEmail());
    }

    @Test
    @DisplayName("CHDEV-303: Soft delete should hide tasks from queries")
    void softDelete_ShouldHideTasks() {
        // Given: Create task
        TenantContext.setTenantId(TENANT_1);
        Task task = createTask(project1, "Task to delete");
        UUID taskId = task.getId();

        entityManager.flush();
        entityManager.clear();

        // When: Soft delete task
        taskRepository.delete(task);
        entityManager.flush();
        entityManager.clear();

        // Then: Should not find task in normal queries
        TenantContext.setTenantId(TENANT_1);
        Optional<Task> notFound = taskRepository.findByIdAndTenantId(taskId, TENANT_1);
        assertTrue(notFound.isEmpty());

        Page<Task> allTasks = taskRepository.findAllByTenantId(TENANT_1, PageRequest.of(0, 10));
        assertEquals(0, allTasks.getTotalElements());
    }

    @Test
    @DisplayName("CHDEV-303: Should respect pagination across tenants")
    void pagination_ShouldRespectTenantBoundaries() {
        // Given: Create 15 tasks in Tenant 1, 10 tasks in Tenant 2
        TenantContext.setTenantId(TENANT_1);
        for (int i = 0; i < 15; i++) {
            createTask(project1, "T1 Task " + i);
        }

        TenantContext.setTenantId(TENANT_2);
        for (int i = 0; i < 10; i++) {
            createTask(project2, "T2 Task " + i);
        }

        entityManager.flush();
        entityManager.clear();
        TenantContext.clear();

        // When: Query page from Tenant 1
        TenantContext.setTenantId(TENANT_1);
        Page<Task> page = taskRepository.findAllByTenantId(TENANT_1, PageRequest.of(0, 10));

        // Then: Should return correct page for Tenant 1 only
        assertEquals(15, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
        assertEquals(10, page.getContent().size());
        assertTrue(page.getContent().stream()
                .allMatch(t -> t.getTenantId().equals(TENANT_1)));
    }

    // Helper methods
    private User createUser(String tenantId, String email) {
        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(email);
        user.setPassword("password");
        user.setFullName("Test User");
        user.setRole(Role.CLIENT);
        return userRepository.save(user);
    }

    private Project createProject(String tenantId, String title, User owner) {
        Project project = new Project(tenantId, title, "Description", null, 
                ProjectStatus.IN_PROGRESS, null, owner);
        return projectRepository.save(project);
    }

    private Task createTask(Project project, String title) {
        Task task = new Task();
        task.setTenantId(project.getTenantId());
        task.setTitle(title);
        task.setDescription("Test description");
        task.setProject(project);
        task.setStatus(TaskStatus.TODO);
        task.setPriority(TaskPriority.MEDIUM);
        task.setDueDate(LocalDateTime.now().plusDays(7));
        task.setEstimatedHours(8); // Required field
        return taskRepository.save(task);
    }
}
