package com.clienthub.application.service;

import com.clienthub.application.dto.project.ProjectActivityResponse;
import com.clienthub.application.dto.project.ProjectFileResponse;
import com.clienthub.common.context.TenantContext;
import com.clienthub.domain.entity.AuditLog;
import com.clienthub.domain.entity.Comment;
import com.clienthub.domain.entity.CommunicationThread;
import com.clienthub.domain.entity.Project;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.AuditAction;
import com.clienthub.domain.enums.CommentTargetType;
import com.clienthub.domain.enums.ProjectStatus;
import com.clienthub.domain.enums.Role;
import com.clienthub.domain.repository.AuditLogRepository;
import com.clienthub.domain.repository.CommentRepository;
import com.clienthub.domain.repository.InvoiceRepository;
import com.clienthub.domain.repository.ProjectMemberRepository;
import com.clienthub.domain.repository.ProjectRepository;
import com.clienthub.domain.repository.TaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;

@ExtendWith(MockitoExtension.class)
class ProjectPortalServiceTest {

    private static final String TENANT_ID = "default";
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID OUTSIDER_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();
    private static final Long INVOICE_ID = 42L;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditProofReader auditProofReader;

    @InjectMocks
    private ProjectPortalService projectPortalService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Project files include attachments from project, task, and invoice comments")
    void getProjectFiles_AggregatesScopedCommentAttachments() {
        Project project = createProject(OWNER_ID);
        Comment projectComment = createComment(10L, CommentTargetType.PROJECT, PROJECT_ID.toString(), OWNER_ID,
                List.of("/attachments/project-brief.pdf"));
        Comment taskComment = createComment(11L, CommentTargetType.TASK, TASK_ID.toString(), MEMBER_ID,
                List.of("http://localhost:8080/api/attachments/task%20screenshot.png"));
        Comment invoiceComment = createComment(12L, CommentTargetType.INVOICE, INVOICE_ID.toString(), MEMBER_ID,
                List.of("/attachments/invoice-note.txt"));

        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID)).thenReturn(Optional.of(project));
        when(taskRepository.findIdsByProjectIdAndTenantId(PROJECT_ID, TENANT_ID)).thenReturn(List.of(TASK_ID));
        when(invoiceRepository.findIdsByProjectIdAndTenantId(PROJECT_ID, TENANT_ID)).thenReturn(List.of(INVOICE_ID));
        when(commentRepository.findProjectScopedComments(
                eq(TENANT_ID),
                eq(PROJECT_ID.toString()),
                eq(List.of(TASK_ID.toString())),
                eq(List.of(INVOICE_ID.toString()))
        )).thenReturn(List.of(projectComment, taskComment, invoiceComment));

        List<ProjectFileResponse> files = projectPortalService.getProjectFiles(PROJECT_ID, OWNER_ID, Role.CLIENT);

        assertEquals(3, files.size());
        assertEquals("project-brief.pdf", files.get(0).getFileName());
        assertEquals("task screenshot.png", files.get(1).getFileName());
        assertEquals("TASK", files.get(1).getSourceType());
        assertEquals("invoice-note.txt", files.get(2).getFileName());
        verify(projectRepository).findByIdAndTenantId(PROJECT_ID, TENANT_ID);
    }

    @Test
    @DisplayName("Project files reject users who are neither owner, member, nor admin")
    void getProjectFiles_RejectsOutsider() {
        Project project = createProject(OWNER_ID);

        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID)).thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByIdProjectIdAndIdUserIdAndTenantId(PROJECT_ID, OUTSIDER_ID, TENANT_ID))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> projectPortalService.getProjectFiles(PROJECT_ID, OUTSIDER_ID, Role.FREELANCER));

        verify(commentRepository, never()).findProjectScopedComments(
                eq(TENANT_ID),
                eq(PROJECT_ID.toString()),
                anyCollection(),
                anyCollection()
        );
    }

    @Test
    @DisplayName("Project activity returns a client-safe timeline without raw audit data")
    void getProjectActivity_ReturnsSanitizedTimeline() {
        Project project = createProject(OWNER_ID);
        Comment comment = createComment(11L, CommentTargetType.TASK, TASK_ID.toString(), MEMBER_ID, List.of());
        AuditLog auditLog = new AuditLog(
                TENANT_ID,
                MEMBER_ID,
                "freelancer@demo.com",
                "FREELANCER",
                AuditAction.CREATE,
                "TASK",
                TASK_ID.toString(),
                "{\"internal\":\"before\"}",
                "{\"internal\":\"after\"}",
                "127.0.0.1",
                "abc123"
        );

        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID)).thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByIdProjectIdAndIdUserIdAndTenantId(PROJECT_ID, MEMBER_ID, TENANT_ID))
                .thenReturn(true);
        when(taskRepository.findIdsByProjectIdAndTenantId(PROJECT_ID, TENANT_ID)).thenReturn(List.of(TASK_ID));
        when(invoiceRepository.findIdsByProjectIdAndTenantId(PROJECT_ID, TENANT_ID)).thenReturn(List.of(INVOICE_ID));
        when(commentRepository.findProjectScopedComments(
                eq(TENANT_ID),
                eq(PROJECT_ID.toString()),
                eq(List.of(TASK_ID.toString())),
                eq(List.of(INVOICE_ID.toString()))
        )).thenReturn(List.of(comment));
        when(auditLogRepository.findProjectActivity(
                eq(TENANT_ID),
                eq(PROJECT_ID.toString()),
                eq(List.of(TASK_ID.toString())),
                eq(List.of(INVOICE_ID.toString())),
                eq(List.of("11")),
                eq(PageRequest.of(0, 20))
        )).thenReturn(new PageImpl<>(List.of(auditLog)));
        when(auditProofReader.getUserProofs(any())).thenReturn(java.util.Map.of());

        Page<ProjectActivityResponse> activity = projectPortalService.getProjectActivity(
                PROJECT_ID,
                MEMBER_ID,
                Role.FREELANCER,
                PageRequest.of(0, 20)
        );

        ProjectActivityResponse item = activity.getContent().get(0);
        assertEquals("CREATE", item.getAction());
        assertEquals("Task created", item.getLabel());
        assertEquals("TASK", item.getEntityType());
        assertEquals("freelancer@demo.com", item.getActorName());
        assertTrue(activity.getContent().stream().noneMatch(response -> "127.0.0.1".equals(response.getActorName())));
    }

    @Test
    void getActivityProof_RejectsAuditRecordOutsideProjectScope() {
        Project project = createProject(OWNER_ID);
        AuditLog unrelated = new AuditLog(TENANT_ID, OWNER_ID, "client@demo.com", "CLIENT",
                AuditAction.UPDATE, "TASK", UUID.randomUUID().toString(), null, "{}", null, "hash");
        setAuditId(unrelated, 99L);

        when(projectRepository.findByIdAndTenantId(PROJECT_ID, TENANT_ID)).thenReturn(Optional.of(project));
        when(taskRepository.findIdsByProjectIdAndTenantId(PROJECT_ID, TENANT_ID)).thenReturn(List.of(TASK_ID));
        when(invoiceRepository.findIdsByProjectIdAndTenantId(PROJECT_ID, TENANT_ID)).thenReturn(List.of(INVOICE_ID));
        when(commentRepository.findProjectScopedComments(any(), any(), any(), any())).thenReturn(List.of());
        when(auditLogRepository.findById(99L)).thenReturn(Optional.of(unrelated));

        assertThrows(com.clienthub.application.exception.ResourceNotFoundException.class,
                () -> projectPortalService.getActivityProof(PROJECT_ID, 99L, OWNER_ID, Role.CLIENT, false));
        verify(auditProofReader, never()).getUserProof(99L);
    }

    private void setAuditId(AuditLog auditLog, Long id) {
        try {
            var field = AuditLog.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(auditLog, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Project createProject(UUID ownerId) {
        User owner = createUser(ownerId, "client@demo.com", "Client Demo", Role.CLIENT);
        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setOwner(owner);
        project.setStatus(ProjectStatus.IN_PROGRESS);
        project.setTenantId(TENANT_ID);
        return project;
    }

    private Comment createComment(Long id, CommentTargetType targetType, String targetId, UUID authorId, List<String> attachments) {
        CommunicationThread thread = new CommunicationThread();
        thread.setId(id + 100);
        thread.setTenantId(TENANT_ID);
        thread.setTargetType(targetType);
        thread.setTargetId(targetId);
        thread.setTopic("General Discussion");
        thread.setAuthor(createUser(OWNER_ID, "client@demo.com", "Client Demo", Role.CLIENT));

        Comment comment = new Comment();
        comment.setId(id);
        comment.setTenantId(TENANT_ID);
        comment.setThread(thread);
        comment.setAuthor(createUser(authorId, authorId.equals(OWNER_ID) ? "client@demo.com" : "freelancer@demo.com",
                authorId.equals(OWNER_ID) ? "Client Demo" : "Freelancer Demo",
                authorId.equals(OWNER_ID) ? Role.CLIENT : Role.FREELANCER));
        comment.setContent("Shared a file");
        comment.setAttachmentUrls(attachments);
        comment.setCreatedAt(Instant.parse("2026-07-05T00:00:00Z"));
        return comment;
    }

    private User createUser(UUID id, String email, String fullName, Role role) {
        return User.builder()
                .id(id)
                .tenantId(TENANT_ID)
                .email(email)
                .password("Password@123")
                .fullName(fullName)
                .role(role)
                .active(true)
                .build();
    }
}
