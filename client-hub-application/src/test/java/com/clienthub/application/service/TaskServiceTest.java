package com.clienthub.application.service;

import com.clienthub.application.dto.task.TaskRequest;
import com.clienthub.application.dto.task.TaskResponse;
import com.clienthub.application.dto.task.TaskSummaryResponse;
import com.clienthub.application.exception.ResourceNotFoundException;
import com.clienthub.application.exception.TaskNotFoundException;
import com.clienthub.application.mapper.TaskMapper;
import com.clienthub.common.context.TenantContext;
import com.clienthub.domain.entity.Project;
import com.clienthub.domain.entity.Task;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.ProjectStatus;
import com.clienthub.domain.enums.Role;
import com.clienthub.domain.enums.TaskPriority;
import com.clienthub.domain.enums.TaskStatus;
import com.clienthub.domain.repository.ProjectMemberRepository;
import com.clienthub.domain.repository.ProjectRepository;
import com.clienthub.domain.repository.TaskRepository;
import com.clienthub.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final String TENANT_ID = "tenant-a";
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OTHER_PROJECT_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID FREELANCER_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID OUTSIDER_ID = UUID.randomUUID();
    private static final UUID OTHER_FREELANCER_ID = UUID.randomUUID();

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private NotificationProducerService notificationProducerService;

    @InjectMocks
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Owning Client creates a task for an eligible project-member Freelancer")
    void createTask_ownerClientAllowed() {
        User owner = user(OWNER_ID, Role.CLIENT, TENANT_ID);
        User assignee = user(FREELANCER_ID, Role.FREELANCER, TENANT_ID);
        Project project = project(PROJECT_ID, owner);
        TaskRequest request = request(PROJECT_ID, FREELANCER_ID);
        Task task = task(project, null);
        TaskResponse response = response();

        when(userRepository.findByIdAndTenantId(OWNER_ID, TENANT_ID)).thenReturn(Optional.of(owner));
        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID)).thenReturn(Optional.of(project));
        when(userRepository.findByIdAndTenantId(FREELANCER_ID, TENANT_ID))
                .thenReturn(Optional.of(assignee));
        when(projectMemberRepository.existsByIdProjectIdAndIdUserIdAndTenantId(
                PROJECT_ID, FREELANCER_ID, TENANT_ID)).thenReturn(true);
        when(taskMapper.toEntity(request)).thenReturn(task);
        when(taskRepository.save(task)).thenReturn(task);
        when(taskMapper.toResponse(task)).thenReturn(response);

        assertSame(response, taskService.createTask(request, OWNER_ID));
        assertSame(project, task.getProject());
        assertSame(assignee, task.getAssignedTo());
    }

    @Test
    @DisplayName("Explicit project-member Freelancer may create only a self-assigned task")
    void createTask_memberFreelancerSelfAssignmentAllowed() {
        User freelancer = user(FREELANCER_ID, Role.FREELANCER, TENANT_ID);
        Project project = project(PROJECT_ID, user(OWNER_ID, Role.CLIENT, TENANT_ID));
        TaskRequest request = request(PROJECT_ID, FREELANCER_ID);
        Task task = task(project, null);

        when(userRepository.findByIdAndTenantId(FREELANCER_ID, TENANT_ID))
                .thenReturn(Optional.of(freelancer));
        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByIdProjectIdAndIdUserIdAndTenantId(
                PROJECT_ID, FREELANCER_ID, TENANT_ID)).thenReturn(true);
        when(taskMapper.toEntity(request)).thenReturn(task);
        when(taskRepository.save(task)).thenReturn(task);
        when(taskMapper.toResponse(task)).thenReturn(response());

        taskService.createTask(request, FREELANCER_ID);

        assertSame(freelancer, task.getAssignedTo());
        verify(taskRepository).save(task);
    }

    @Test
    @DisplayName("Administrator may create an unassigned task within the tenant")
    void createTask_administratorAllowed() {
        User admin = user(ADMIN_ID, Role.ADMIN, TENANT_ID);
        Project project = project(PROJECT_ID, user(OWNER_ID, Role.CLIENT, TENANT_ID));
        TaskRequest request = request(PROJECT_ID, null);
        Task task = task(project, null);

        when(userRepository.findByIdAndTenantId(ADMIN_ID, TENANT_ID)).thenReturn(Optional.of(admin));
        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.of(project));
        when(taskMapper.toEntity(request)).thenReturn(task);
        when(taskRepository.save(task)).thenReturn(task);
        when(taskMapper.toResponse(task)).thenReturn(response());

        taskService.createTask(request, ADMIN_ID);

        verify(taskRepository).save(task);
    }

    @Test
    @DisplayName("Same-tenant outsider Client cannot create project tasks")
    void createTask_sameTenantOutsidersDenied() {
        Project project = project(PROJECT_ID, user(OWNER_ID, Role.CLIENT, TENANT_ID));
        TaskRequest request = request(PROJECT_ID, null);
        User outsiderClient = user(OUTSIDER_ID, Role.CLIENT, TENANT_ID);

        when(userRepository.findByIdAndTenantId(OUTSIDER_ID, TENANT_ID))
                .thenReturn(Optional.of(outsiderClient));
        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.of(project));

        assertThrows(AccessDeniedException.class,
                () -> taskService.createTask(request, OUTSIDER_ID));
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("Non-member Freelancer cannot create even when attempting self-assignment")
    void createTask_nonMemberFreelancerDenied() {
        User freelancer = user(FREELANCER_ID, Role.FREELANCER, TENANT_ID);
        Project project = project(PROJECT_ID, user(OWNER_ID, Role.CLIENT, TENANT_ID));
        TaskRequest request = request(PROJECT_ID, FREELANCER_ID);

        when(userRepository.findByIdAndTenantId(FREELANCER_ID, TENANT_ID))
                .thenReturn(Optional.of(freelancer));
        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByIdProjectIdAndIdUserIdAndTenantId(
                PROJECT_ID, FREELANCER_ID, TENANT_ID)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> taskService.createTask(request, FREELANCER_ID));
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("Freelancer cannot create an unassigned task")
    void createTask_freelancerCannotWidenAssignment() {
        User freelancer = user(FREELANCER_ID, Role.FREELANCER, TENANT_ID);
        Project project = project(PROJECT_ID, user(OWNER_ID, Role.CLIENT, TENANT_ID));
        TaskRequest request = request(PROJECT_ID, null);

        when(userRepository.findByIdAndTenantId(FREELANCER_ID, TENANT_ID))
                .thenReturn(Optional.of(freelancer));
        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByIdProjectIdAndIdUserIdAndTenantId(
                PROJECT_ID, FREELANCER_ID, TENANT_ID)).thenReturn(true);
        when(taskMapper.toEntity(request)).thenReturn(task(project, null));

        assertThrows(AccessDeniedException.class,
                () -> taskService.createTask(request, FREELANCER_ID));
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("Wrong-role and non-member assignees are rejected")
    void createTask_ineligibleAssigneesDenied() {
        User owner = user(OWNER_ID, Role.CLIENT, TENANT_ID);
        User wrongRole = user(OUTSIDER_ID, Role.CLIENT, TENANT_ID);
        Project project = project(PROJECT_ID, owner);
        TaskRequest request = request(PROJECT_ID, OUTSIDER_ID);

        when(userRepository.findByIdAndTenantId(OWNER_ID, TENANT_ID)).thenReturn(Optional.of(owner));
        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.of(project));
        when(taskMapper.toEntity(request)).thenReturn(task(project, null));
        when(userRepository.findByIdAndTenantId(OUTSIDER_ID, TENANT_ID))
                .thenReturn(Optional.of(wrongRole));

        assertThrows(AccessDeniedException.class,
                () -> taskService.createTask(request, OWNER_ID));
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cross-tenant project and assignee identifiers are non-disclosing")
    void createTask_crossTenantTargetsNotFound() {
        User owner = user(OWNER_ID, Role.CLIENT, TENANT_ID);
        TaskRequest request = request(PROJECT_ID, FREELANCER_ID);

        when(userRepository.findByIdAndTenantId(OWNER_ID, TENANT_ID)).thenReturn(Optional.of(owner));
        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> taskService.createTask(request, OWNER_ID));
        verify(userRepository, never()).findByIdAndTenantId(FREELANCER_ID, TENANT_ID);
    }

    @Test
    @DisplayName("Task list uses Client owner scope and preserves filters")
    void getTasks_clientUsesOwnerScopedRepositoryQuery() {
        User owner = user(OWNER_ID, Role.CLIENT, TENANT_ID);
        PageRequest pageable = PageRequest.of(0, 20);
        when(userRepository.findByIdAndTenantId(OWNER_ID, TENANT_ID)).thenReturn(Optional.of(owner));
        when(taskRepository.findVisibleToClient(
                TENANT_ID, OWNER_ID, PROJECT_ID, TaskStatus.TODO,
                TaskPriority.HIGH, FREELANCER_ID, pageable)).thenReturn(Page.empty(pageable));

        taskService.getTasks(PROJECT_ID, TaskStatus.TODO, TaskPriority.HIGH,
                FREELANCER_ID, OWNER_ID, pageable);

        verify(taskRepository).findVisibleToClient(
                TENANT_ID, OWNER_ID, PROJECT_ID, TaskStatus.TODO,
                TaskPriority.HIGH, FREELANCER_ID, pageable);
        verify(taskRepository, never()).findVisibleToAdministrator(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Freelancer list ignores an assignedToId privilege-widening attempt")
    void getTasks_freelancerCannotWidenAssignedScope() {
        User freelancer = user(FREELANCER_ID, Role.FREELANCER, TENANT_ID);
        PageRequest pageable = PageRequest.of(0, 20);
        when(userRepository.findByIdAndTenantId(FREELANCER_ID, TENANT_ID))
                .thenReturn(Optional.of(freelancer));
        when(taskRepository.findVisibleToFreelancer(
                TENANT_ID, FREELANCER_ID, PROJECT_ID, null, null, pageable))
                .thenReturn(Page.empty(pageable));

        taskService.getTasks(PROJECT_ID, null, null, OTHER_FREELANCER_ID,
                FREELANCER_ID, pageable);

        verify(taskRepository).findVisibleToFreelancer(
                TENANT_ID, FREELANCER_ID, PROJECT_ID, null, null, pageable);
    }

    @Test
    @DisplayName("Administrator task list remains tenant scoped")
    void getTasks_administratorUsesTenantScopedRepositoryQuery() {
        User admin = user(ADMIN_ID, Role.ADMIN, TENANT_ID);
        PageRequest pageable = PageRequest.of(0, 20);
        when(userRepository.findByIdAndTenantId(ADMIN_ID, TENANT_ID)).thenReturn(Optional.of(admin));
        when(taskRepository.findVisibleToAdministrator(
                TENANT_ID, null, null, null, null, pageable)).thenReturn(Page.empty(pageable));

        taskService.getTasks(null, null, null, null, ADMIN_ID, pageable);

        verify(taskRepository).findVisibleToAdministrator(
                TENANT_ID, null, null, null, null, pageable);
    }

    @Test
    @DisplayName("Task summary selects the same role relationship population as task list")
    void getTaskSummary_usesRoleScopedCounts() {
        User owner = user(OWNER_ID, Role.CLIENT, TENANT_ID);
        when(userRepository.findByIdAndTenantId(OWNER_ID, TENANT_ID)).thenReturn(Optional.of(owner));
        when(taskRepository.countByProjectOwnerIdAndTenantIdAndStatusIn(
                OWNER_ID, TENANT_ID, List.of(TaskStatus.TODO))).thenReturn(2L);
        when(taskRepository.countByProjectOwnerIdAndTenantIdAndStatusIn(
                OWNER_ID, TENANT_ID, List.of(TaskStatus.IN_PROGRESS))).thenReturn(3L);
        when(taskRepository.countByProjectOwnerIdAndTenantIdAndStatusIn(
                OWNER_ID, TENANT_ID, List.of(TaskStatus.DONE))).thenReturn(4L);

        TaskSummaryResponse summary = taskService.getTaskSummary(OWNER_ID);

        assertEquals(2L, summary.getTodo());
        assertEquals(3L, summary.getInProgress());
        assertEquals(4L, summary.getDone());
        verify(taskRepository, never()).countByTenantIdAndStatusIn(any(), any());
    }

    @Test
    @DisplayName("Owner, assigned Freelancer and Administrator may read task detail")
    void getTaskById_permittedRelationshipsAllowed() {
        User owner = user(OWNER_ID, Role.CLIENT, TENANT_ID);
        Project project = project(PROJECT_ID, owner);
        Task task = task(project, user(FREELANCER_ID, Role.FREELANCER, TENANT_ID));
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(userRepository.findByIdAndTenantId(OWNER_ID, TENANT_ID)).thenReturn(Optional.of(owner));
        when(taskMapper.toResponse(task)).thenReturn(response());

        taskService.getTaskById(TASK_ID, OWNER_ID);

        verify(taskMapper).toResponse(task);
    }

    @Test
    @DisplayName("Assigned Freelancer and Administrator may read task detail")
    void getTaskById_assigneeAndAdministratorAllowed() {
        User owner = user(OWNER_ID, Role.CLIENT, TENANT_ID);
        User freelancer = user(FREELANCER_ID, Role.FREELANCER, TENANT_ID);
        User admin = user(ADMIN_ID, Role.ADMIN, TENANT_ID);
        Task task = task(project(PROJECT_ID, owner), freelancer);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(userRepository.findByIdAndTenantId(FREELANCER_ID, TENANT_ID))
                .thenReturn(Optional.of(freelancer));
        when(userRepository.findByIdAndTenantId(ADMIN_ID, TENANT_ID)).thenReturn(Optional.of(admin));
        when(taskMapper.toResponse(task)).thenReturn(response());

        taskService.getTaskById(TASK_ID, FREELANCER_ID);
        taskService.getTaskById(TASK_ID, ADMIN_ID);

        verify(taskMapper, times(2)).toResponse(task);
    }

    @Test
    @DisplayName("Same-tenant Client outsider is denied task detail")
    void getTaskById_sameTenantClientOutsiderDenied() {
        Task task = task(
                project(PROJECT_ID, user(OWNER_ID, Role.CLIENT, TENANT_ID)),
                user(FREELANCER_ID, Role.FREELANCER, TENANT_ID));
        User outsider = user(OUTSIDER_ID, Role.CLIENT, TENANT_ID);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(userRepository.findByIdAndTenantId(OUTSIDER_ID, TENANT_ID))
                .thenReturn(Optional.of(outsider));

        assertThrows(AccessDeniedException.class,
                () -> taskService.getTaskById(TASK_ID, OUTSIDER_ID));
    }

    @Test
    @DisplayName("Non-assigned project-member Freelancer is denied task detail")
    void getTaskById_nonAssignedFreelancerDenied() {
        Task task = task(
                project(PROJECT_ID, user(OWNER_ID, Role.CLIENT, TENANT_ID)),
                user(FREELANCER_ID, Role.FREELANCER, TENANT_ID));
        User otherFreelancer = user(OTHER_FREELANCER_ID, Role.FREELANCER, TENANT_ID);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(userRepository.findByIdAndTenantId(OTHER_FREELANCER_ID, TENANT_ID))
                .thenReturn(Optional.of(otherFreelancer));

        assertThrows(AccessDeniedException.class,
                () -> taskService.getTaskById(TASK_ID, OTHER_FREELANCER_ID));
    }

    @Test
    @DisplayName("Related UUID under the wrong role does not inherit owner or assignee access")
    void getTaskById_wrongRoleRelatedActorsDenied() {
        User actualOwner = user(OWNER_ID, Role.CLIENT, TENANT_ID);
        Task task = task(
                project(PROJECT_ID, actualOwner),
                user(FREELANCER_ID, Role.FREELANCER, TENANT_ID));
        User ownerUuidWithWrongRole = user(OWNER_ID, Role.FREELANCER, TENANT_ID);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(userRepository.findByIdAndTenantId(OWNER_ID, TENANT_ID))
                .thenReturn(Optional.of(ownerUuidWithWrongRole));

        assertThrows(AccessDeniedException.class,
                () -> taskService.getTaskById(TASK_ID, OWNER_ID));
    }

    @Test
    @DisplayName("Missing or cross-tenant task is non-disclosing")
    void getTaskById_crossTenantTaskNotFound() {
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThrows(TaskNotFoundException.class,
                () -> taskService.getTaskById(TASK_ID, OWNER_ID));
        verify(userRepository, never()).findByIdAndTenantId(OWNER_ID, TENANT_ID);
    }

    @Test
    @DisplayName("Assigned Freelancer may update core fields but cannot move or reassign task")
    void updateTask_freelancerCannotEscalate() {
        User owner = user(OWNER_ID, Role.CLIENT, TENANT_ID);
        User freelancer = user(FREELANCER_ID, Role.FREELANCER, TENANT_ID);
        Project project = project(PROJECT_ID, owner);
        Task task = task(project, freelancer);
        TaskRequest request = request(OTHER_PROJECT_ID, FREELANCER_ID);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(userRepository.findByIdAndTenantId(FREELANCER_ID, TENANT_ID))
                .thenReturn(Optional.of(freelancer));

        assertThrows(AccessDeniedException.class,
                () -> taskService.updateTask(TASK_ID, request, FREELANCER_ID));
        verify(projectRepository, never()).findByIdAndTenantId(OTHER_PROJECT_ID, TENANT_ID);
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("Owning Client cannot move a task into another Client's project")
    void updateTask_crossProjectOwnerEscalationDenied() {
        User owner = user(OWNER_ID, Role.CLIENT, TENANT_ID);
        Project project = project(PROJECT_ID, owner);
        Project otherProject = project(OTHER_PROJECT_ID, user(OUTSIDER_ID, Role.CLIENT, TENANT_ID));
        Task task = task(project, null);
        TaskRequest request = request(OTHER_PROJECT_ID, null);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(userRepository.findByIdAndTenantId(OWNER_ID, TENANT_ID)).thenReturn(Optional.of(owner));
        when(projectRepository.findByIdAndTenantId(OTHER_PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.of(otherProject));

        assertThrows(AccessDeniedException.class,
                () -> taskService.updateTask(TASK_ID, request, OWNER_ID));
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("Owning Client may move a task only to another owned project with eligible assignee")
    void updateTask_ownerValidatesTargetProjectAndAssignee() {
        User owner = user(OWNER_ID, Role.CLIENT, TENANT_ID);
        User freelancer = user(FREELANCER_ID, Role.FREELANCER, TENANT_ID);
        Project project = project(PROJECT_ID, owner);
        Project targetProject = project(OTHER_PROJECT_ID, owner);
        Task task = task(project, freelancer);
        TaskRequest request = request(OTHER_PROJECT_ID, FREELANCER_ID);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(userRepository.findByIdAndTenantId(OWNER_ID, TENANT_ID)).thenReturn(Optional.of(owner));
        when(projectRepository.findByIdAndTenantId(OTHER_PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.of(targetProject));
        when(userRepository.findByIdAndTenantId(FREELANCER_ID, TENANT_ID))
                .thenReturn(Optional.of(freelancer));
        when(projectMemberRepository.existsByIdProjectIdAndIdUserIdAndTenantId(
                OTHER_PROJECT_ID, FREELANCER_ID, TENANT_ID)).thenReturn(true);
        when(taskRepository.save(task)).thenReturn(task);
        when(taskMapper.toResponse(task)).thenReturn(response());

        taskService.updateTask(TASK_ID, request, OWNER_ID);

        assertSame(targetProject, task.getProject());
        assertSame(freelancer, task.getAssignedTo());
    }

    @Test
    @DisplayName("Assigned Freelancer may transition status; outsider cannot")
    void updateTaskStatus_enforcesTaskRelationship() {
        User freelancer = user(FREELANCER_ID, Role.FREELANCER, TENANT_ID);
        Task task = task(
                project(PROJECT_ID, user(OWNER_ID, Role.CLIENT, TENANT_ID)), freelancer);
        task.setStatus(TaskStatus.TODO);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(userRepository.findByIdAndTenantId(FREELANCER_ID, TENANT_ID))
                .thenReturn(Optional.of(freelancer));
        when(taskRepository.save(task)).thenReturn(task);
        when(taskMapper.toResponse(task)).thenReturn(response());

        taskService.updateTaskStatus(TASK_ID, TaskStatus.IN_PROGRESS, FREELANCER_ID);

        assertEquals(TaskStatus.IN_PROGRESS, task.getStatus());
    }

    @Test
    @DisplayName("Same-tenant outsider cannot update, transition, assign or unassign a task")
    void sameTenantOutsider_cannotMutateTask() {
        User outsider = user(OUTSIDER_ID, Role.CLIENT, TENANT_ID);
        Task task = task(
                project(PROJECT_ID, user(OWNER_ID, Role.CLIENT, TENANT_ID)),
                user(FREELANCER_ID, Role.FREELANCER, TENANT_ID));
        TaskRequest request = request(PROJECT_ID, null);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(userRepository.findByIdAndTenantId(OUTSIDER_ID, TENANT_ID))
                .thenReturn(Optional.of(outsider));

        assertThrows(AccessDeniedException.class,
                () -> taskService.updateTask(TASK_ID, request, OUTSIDER_ID));
        assertThrows(AccessDeniedException.class,
                () -> taskService.updateTaskStatus(TASK_ID, TaskStatus.IN_PROGRESS, OUTSIDER_ID));
        assertThrows(AccessDeniedException.class,
                () -> taskService.assignTask(TASK_ID, OTHER_FREELANCER_ID, OUTSIDER_ID));
        assertThrows(AccessDeniedException.class,
                () -> taskService.unassignTask(TASK_ID, OUTSIDER_ID));
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("Only owning Client or Administrator may assign an eligible member Freelancer")
    void assignTask_ownerAllowedAndOutsiderDenied() {
        User owner = user(OWNER_ID, Role.CLIENT, TENANT_ID);
        User freelancer = user(FREELANCER_ID, Role.FREELANCER, TENANT_ID);
        Task task = task(project(PROJECT_ID, owner), null);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(userRepository.findByIdAndTenantId(OWNER_ID, TENANT_ID)).thenReturn(Optional.of(owner));
        when(userRepository.findByIdAndTenantId(FREELANCER_ID, TENANT_ID))
                .thenReturn(Optional.of(freelancer));
        when(projectMemberRepository.existsByIdProjectIdAndIdUserIdAndTenantId(
                PROJECT_ID, FREELANCER_ID, TENANT_ID)).thenReturn(true);
        when(taskRepository.save(task)).thenReturn(task);
        when(taskMapper.toResponse(task)).thenReturn(response());

        taskService.assignTask(TASK_ID, FREELANCER_ID, OWNER_ID);

        assertSame(freelancer, task.getAssignedTo());
    }

    @Test
    @DisplayName("Assign rejects non-member, wrong-role and cross-tenant targets before mutation")
    void assignTask_ineligibleTargetDenied() {
        User owner = user(OWNER_ID, Role.CLIENT, TENANT_ID);
        User freelancer = user(FREELANCER_ID, Role.FREELANCER, TENANT_ID);
        Task task = task(project(PROJECT_ID, owner), null);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(userRepository.findByIdAndTenantId(OWNER_ID, TENANT_ID)).thenReturn(Optional.of(owner));
        when(userRepository.findByIdAndTenantId(FREELANCER_ID, TENANT_ID))
                .thenReturn(Optional.of(freelancer));
        when(projectMemberRepository.existsByIdProjectIdAndIdUserIdAndTenantId(
                PROJECT_ID, FREELANCER_ID, TENANT_ID)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> taskService.assignTask(TASK_ID, FREELANCER_ID, OWNER_ID));
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cross-tenant assignee identifier is non-disclosing")
    void assignTask_crossTenantAssigneeNotFound() {
        User owner = user(OWNER_ID, Role.CLIENT, TENANT_ID);
        Task task = task(project(PROJECT_ID, owner), null);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(userRepository.findByIdAndTenantId(OWNER_ID, TENANT_ID)).thenReturn(Optional.of(owner));
        when(userRepository.findByIdAndTenantId(FREELANCER_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> taskService.assignTask(TASK_ID, FREELANCER_ID, OWNER_ID));
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("Assigned Freelancer may self-unassign but unrelated Freelancer cannot")
    void unassignTask_assigneeAllowed() {
        User freelancer = user(FREELANCER_ID, Role.FREELANCER, TENANT_ID);
        Task task = task(
                project(PROJECT_ID, user(OWNER_ID, Role.CLIENT, TENANT_ID)), freelancer);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(userRepository.findByIdAndTenantId(FREELANCER_ID, TENANT_ID))
                .thenReturn(Optional.of(freelancer));
        when(taskRepository.save(task)).thenReturn(task);
        when(taskMapper.toResponse(task)).thenReturn(response());

        taskService.unassignTask(TASK_ID, FREELANCER_ID);

        assertNull(task.getAssignedTo());
    }

    @Test
    @DisplayName("Delete requires owning Client or tenant Administrator")
    void deleteTask_sameTenantOutsiderDenied() {
        User outsider = user(OUTSIDER_ID, Role.CLIENT, TENANT_ID);
        Task task = task(
                project(PROJECT_ID, user(OWNER_ID, Role.CLIENT, TENANT_ID)), null);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(userRepository.findByIdAndTenantId(OUTSIDER_ID, TENANT_ID))
                .thenReturn(Optional.of(outsider));

        assertThrows(AccessDeniedException.class,
                () -> taskService.deleteTask(TASK_ID, OUTSIDER_ID));
        verify(taskRepository, never()).delete(any(Task.class));
    }

    @Test
    @DisplayName("Administrator may read, transition, assign, unassign and delete tenant task")
    void administrator_taskLifecycleAllowed() {
        User admin = user(ADMIN_ID, Role.ADMIN, TENANT_ID);
        User freelancer = user(FREELANCER_ID, Role.FREELANCER, TENANT_ID);
        Task task = task(
                project(PROJECT_ID, user(OWNER_ID, Role.CLIENT, TENANT_ID)), null);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(userRepository.findByIdAndTenantId(ADMIN_ID, TENANT_ID)).thenReturn(Optional.of(admin));
        when(userRepository.findByIdAndTenantId(FREELANCER_ID, TENANT_ID))
                .thenReturn(Optional.of(freelancer));
        when(projectMemberRepository.existsByIdProjectIdAndIdUserIdAndTenantId(
                PROJECT_ID, FREELANCER_ID, TENANT_ID)).thenReturn(true);
        when(taskRepository.save(task)).thenReturn(task);
        when(taskMapper.toResponse(task)).thenReturn(response());

        taskService.getTaskById(TASK_ID, ADMIN_ID);
        taskService.updateTaskStatus(TASK_ID, TaskStatus.IN_PROGRESS, ADMIN_ID);
        taskService.assignTask(TASK_ID, FREELANCER_ID, ADMIN_ID);
        taskService.unassignTask(TASK_ID, ADMIN_ID);
        taskService.deleteTask(TASK_ID, ADMIN_ID);

        verify(taskRepository).delete(task);
    }

    @Test
    @DisplayName("Missing tenant context blocks every task query before repository access")
    void taskOperation_missingTenantContextDenied() {
        TenantContext.clear();

        assertThrows(SecurityException.class,
                () -> taskService.getTaskById(TASK_ID, OWNER_ID));
        verify(taskRepository, never()).findByIdAndTenantId(any(), any());
    }

    private TaskRequest request(UUID projectId, UUID assigneeId) {
        TaskRequest request = new TaskRequest();
        request.setProjectId(projectId);
        request.setAssignedToId(assigneeId);
        request.setTitle("Relationship-scoped task");
        request.setPriority(TaskPriority.HIGH);
        request.setStatus(TaskStatus.TODO);
        request.setEstimatedHours(3);
        return request;
    }

    private User user(UUID id, Role role, String tenantId) {
        return User.builder()
                .id(id)
                .tenantId(tenantId)
                .email(id + "@example.test")
                .password("not-used")
                .fullName(role.name())
                .role(role)
                .active(true)
                .build();
    }

    private Project project(UUID id, User owner) {
        Project project = new Project();
        project.setId(id);
        project.setTenantId(TENANT_ID);
        project.setTitle("Project");
        project.setStatus(ProjectStatus.IN_PROGRESS);
        project.setOwner(owner);
        return project;
    }

    private Task task(Project project, User assignee) {
        Task task = new Task();
        task.setId(TASK_ID);
        task.setTenantId(TENANT_ID);
        task.setTitle("Task");
        task.setStatus(TaskStatus.TODO);
        task.setPriority(TaskPriority.HIGH);
        task.setEstimatedHours(3);
        task.setProject(project);
        task.setAssignedTo(assignee);
        return task;
    }

    private TaskResponse response() {
        TaskResponse response = new TaskResponse();
        response.setId(TASK_ID);
        response.setTitle("Task");
        response.setStatus(TaskStatus.TODO);
        response.setPriority(TaskPriority.HIGH);
        return response;
    }
}
