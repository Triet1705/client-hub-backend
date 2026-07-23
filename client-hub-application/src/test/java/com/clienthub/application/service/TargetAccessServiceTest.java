package com.clienthub.application.service;

import com.clienthub.application.exception.ResourceNotFoundException;
import com.clienthub.common.context.TenantContext;
import com.clienthub.domain.entity.Invoice;
import com.clienthub.domain.entity.Project;
import com.clienthub.domain.entity.Task;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.CommentTargetType;
import com.clienthub.domain.enums.Role;
import com.clienthub.domain.repository.InvoiceRepository;
import com.clienthub.domain.repository.ProjectMemberRepository;
import com.clienthub.domain.repository.ProjectRepository;
import com.clienthub.domain.repository.TaskRepository;
import com.clienthub.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TargetAccessServiceTest {

    private static final String TENANT_ID = "tenant-a";
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();
    private static final long INVOICE_ID = 42L;
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID FREELANCER_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID OUTSIDER_ID = UUID.randomUUID();

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private UserRepository userRepository;

    private TargetAccessService targetAccessService;
    private User owner;
    private User freelancer;
    private User admin;
    private Project project;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        targetAccessService = new TargetAccessService(
                projectRepository,
                projectMemberRepository,
                taskRepository,
                invoiceRepository,
                userRepository);
        owner = user(OWNER_ID, Role.CLIENT);
        freelancer = user(FREELANCER_ID, Role.FREELANCER);
        admin = user(ADMIN_ID, Role.ADMIN);
        project = new Project();
        project.setId(PROJECT_ID);
        project.setTenantId(TENANT_ID);
        project.setOwner(owner);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void projectOwnerMemberFreelancerAndAdministratorAreAuthorized() {
        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.of(project));
        when(userRepository.findByIdAndTenantId(OWNER_ID, TENANT_ID)).thenReturn(Optional.of(owner));
        when(userRepository.findByIdAndTenantId(FREELANCER_ID, TENANT_ID)).thenReturn(Optional.of(freelancer));
        when(userRepository.findByIdAndTenantId(ADMIN_ID, TENANT_ID)).thenReturn(Optional.of(admin));
        when(projectMemberRepository.existsByIdProjectIdAndIdUserIdAndTenantId(
                PROJECT_ID, FREELANCER_ID, TENANT_ID)).thenReturn(true);

        assertDoesNotThrow(() -> targetAccessService.authorize(
                CommentTargetType.PROJECT, PROJECT_ID.toString(), OWNER_ID));
        assertDoesNotThrow(() -> targetAccessService.authorize(
                CommentTargetType.PROJECT, PROJECT_ID.toString(), FREELANCER_ID));
        assertDoesNotThrow(() -> targetAccessService.authorize(
                CommentTargetType.PROJECT, PROJECT_ID.toString(), ADMIN_ID));
    }

    @Test
    void unrelatedProjectActorAndClientMembershipEscalationAreDenied() {
        User outsider = user(OUTSIDER_ID, Role.CLIENT);
        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.of(project));
        when(userRepository.findByIdAndTenantId(OUTSIDER_ID, TENANT_ID))
                .thenReturn(Optional.of(outsider));

        assertThrows(AccessDeniedException.class, () -> targetAccessService.authorize(
                CommentTargetType.PROJECT, PROJECT_ID.toString(), OUTSIDER_ID));
        verify(projectMemberRepository, never())
                .existsByIdProjectIdAndIdUserIdAndTenantId(PROJECT_ID, OUTSIDER_ID, TENANT_ID);
    }

    @Test
    void crossTenantProjectIsNonDisclosingNotFoundBeforeActorLookup() {
        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> targetAccessService.authorize(
                CommentTargetType.PROJECT, PROJECT_ID.toString(), OWNER_ID));
        verifyNoInteractions(userRepository, projectMemberRepository);
    }

    @Test
    void taskOwnerAssigneeAndAdministratorAreAuthorized() {
        Task task = task(project, freelancer);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(userRepository.findByIdAndTenantId(OWNER_ID, TENANT_ID)).thenReturn(Optional.of(owner));
        when(userRepository.findByIdAndTenantId(FREELANCER_ID, TENANT_ID)).thenReturn(Optional.of(freelancer));
        when(userRepository.findByIdAndTenantId(ADMIN_ID, TENANT_ID)).thenReturn(Optional.of(admin));

        assertDoesNotThrow(() -> targetAccessService.authorize(
                CommentTargetType.TASK, TASK_ID.toString(), OWNER_ID));
        assertDoesNotThrow(() -> targetAccessService.authorize(
                CommentTargetType.TASK, TASK_ID.toString(), FREELANCER_ID));
        assertDoesNotThrow(() -> targetAccessService.authorize(
                CommentTargetType.TASK, TASK_ID.toString(), ADMIN_ID));
    }

    @Test
    void projectMemberWhoIsNotTaskAssigneeIsDenied() {
        UUID otherFreelancerId = UUID.randomUUID();
        User otherFreelancer = user(otherFreelancerId, Role.FREELANCER);
        Task task = task(project, freelancer);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(userRepository.findByIdAndTenantId(otherFreelancerId, TENANT_ID))
                .thenReturn(Optional.of(otherFreelancer));

        assertThrows(AccessDeniedException.class, () -> targetAccessService.authorize(
                CommentTargetType.TASK, TASK_ID.toString(), otherFreelancerId));
    }

    @Test
    void invoiceClientFreelancerAndAdministratorAreAuthorized() {
        Invoice invoice = invoice(owner, freelancer);
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID))
                .thenReturn(Optional.of(invoice));
        when(userRepository.findByIdAndTenantId(OWNER_ID, TENANT_ID)).thenReturn(Optional.of(owner));
        when(userRepository.findByIdAndTenantId(FREELANCER_ID, TENANT_ID)).thenReturn(Optional.of(freelancer));
        when(userRepository.findByIdAndTenantId(ADMIN_ID, TENANT_ID)).thenReturn(Optional.of(admin));

        assertDoesNotThrow(() -> targetAccessService.authorize(
                CommentTargetType.INVOICE, String.valueOf(INVOICE_ID), OWNER_ID));
        assertDoesNotThrow(() -> targetAccessService.authorize(
                CommentTargetType.INVOICE, String.valueOf(INVOICE_ID), FREELANCER_ID));
        assertDoesNotThrow(() -> targetAccessService.authorize(
                CommentTargetType.INVOICE, String.valueOf(INVOICE_ID), ADMIN_ID));
    }

    @Test
    void projectMemberWhoIsNotInvoicePartyIsDenied() {
        User outsider = user(OUTSIDER_ID, Role.FREELANCER);
        Invoice invoice = invoice(owner, freelancer);
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID))
                .thenReturn(Optional.of(invoice));
        when(userRepository.findByIdAndTenantId(OUTSIDER_ID, TENANT_ID))
                .thenReturn(Optional.of(outsider));

        assertThrows(AccessDeniedException.class, () -> targetAccessService.authorize(
                CommentTargetType.INVOICE, String.valueOf(INVOICE_ID), OUTSIDER_ID));
        verifyNoInteractions(projectMemberRepository);
    }

    @Test
    void invoiceIdentifierCannotEscalateAUserWithTheWrongRole() {
        User wrongRole = user(FREELANCER_ID, Role.CLIENT);
        Invoice invoice = invoice(owner, freelancer);
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID))
                .thenReturn(Optional.of(invoice));
        when(userRepository.findByIdAndTenantId(FREELANCER_ID, TENANT_ID))
                .thenReturn(Optional.of(wrongRole));

        AccessDeniedException exception = assertThrows(AccessDeniedException.class,
                () -> targetAccessService.authorize(
                        CommentTargetType.INVOICE, String.valueOf(INVOICE_ID), FREELANCER_ID));
        assertEquals("You are not allowed to access this invoice", exception.getMessage());
    }

    @Test
    void malformedTargetIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> targetAccessService.authorize(
                CommentTargetType.PROJECT, "../project", OWNER_ID));
        assertThrows(IllegalArgumentException.class, () -> targetAccessService.authorize(
                CommentTargetType.INVOICE, "not-a-number", OWNER_ID));
        verifyNoInteractions(projectRepository, invoiceRepository, userRepository);
    }

    private User user(UUID id, Role role) {
        return User.builder()
                .id(id)
                .tenantId(TENANT_ID)
                .email(id + "@example.com")
                .password("password")
                .fullName(role.name())
                .role(role)
                .build();
    }

    private Task task(Project taskProject, User assignee) {
        Task task = new Task();
        task.setId(TASK_ID);
        task.setTenantId(TENANT_ID);
        task.setProject(taskProject);
        task.setAssignedTo(assignee);
        return task;
    }

    private Invoice invoice(User client, User invoiceFreelancer) {
        Invoice invoice = new Invoice();
        invoice.setId(INVOICE_ID);
        invoice.setTenantId(TENANT_ID);
        invoice.setProject(project);
        invoice.setClient(client);
        invoice.setFreelancer(invoiceFreelancer);
        return invoice;
    }
}
