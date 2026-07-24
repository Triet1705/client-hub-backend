package com.clienthub.web.integration;

import com.clienthub.common.context.TenantContext;
import com.clienthub.domain.entity.Project;
import com.clienthub.domain.entity.ProjectMember;
import com.clienthub.domain.entity.ProjectMemberId;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.ProjectStatus;
import com.clienthub.domain.enums.Role;
import com.clienthub.domain.repository.ProjectMemberRepository;
import com.clienthub.domain.repository.ProjectRepository;
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.http.MediaType;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ClientHubBackendApplication.class)
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MembershipSearchPostgresIntegrationTest {

    private static final String TENANT_A = "mem-search-tenant-a";
    private static final String TENANT_B = "mem-search-tenant-b";

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
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;
    private User administrator;
    private User outsiderClient;
    private User foreignAdministrator;
    private User foreignFreelancer;
    private Project project;

    @BeforeEach
    void setUp() {
        cleanFixtureData();

        TenantContext.setTenantId(TENANT_A);
        owner = createUser(TENANT_A, "owner@mem-a.test", "Owner Client", Role.CLIENT, true);
        administrator = createUser(TENANT_A, "admin@mem-a.test", "Tenant Administrator", Role.ADMIN, true);
        outsiderClient = createUser(TENANT_A, "outsider@mem-a.test", "Outsider Client", Role.CLIENT, true);
        User alice = createUser(TENANT_A, "alice.freelancer@mem-a.test", "Alice Freelancer",
                Role.FREELANCER, true);
        createUser(TENANT_A, "bob.freelancer@mem-a.test", "Bob Freelancer", Role.FREELANCER, true);
        createUser(TENANT_A, "inactive@mem-a.test", "Inactive Freelancer", Role.FREELANCER, false);
        createUser(TENANT_A, "wrong-role@mem-a.test", "Wrong Role", Role.CLIENT, true);
        User existingMember = createUser(TENANT_A, "member@mem-a.test", "Zzz Existing Member",
                Role.FREELANCER, true);
        project = createProject(TENANT_A, "Membership Search Project", owner);
        addMember(project, existingMember);

        TenantContext.setTenantId(TENANT_B);
        User foreignOwner = createUser(TENANT_B, "owner@mem-b.test", "Foreign Owner", Role.CLIENT, true);
        foreignAdministrator = createUser(
                TENANT_B, "admin@mem-b.test", "Foreign Administrator", Role.ADMIN, true);
        foreignFreelancer = createUser(TENANT_B, "foreign.freelancer@mem-b.test", "Foreign Freelancer",
                Role.FREELANCER, true);
        createProject(TENANT_B, "Foreign Project", foreignOwner);

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
    @DisplayName("DEFECT-MEM-01: Administrator omitted keyword succeeds on PostgreSQL")
    void administratorOmittedKeyword_ShouldReturnOnlyEligibleTenantFreelancers() throws Exception {
        performSearch(administrator, TENANT_A, project.getId(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].email", containsInAnyOrder(
                        "alice.freelancer@mem-a.test",
                        "bob.freelancer@mem-a.test")))
                .andExpect(jsonPath("$[*].email", not(hasItem("inactive@mem-a.test"))))
                .andExpect(jsonPath("$[*].email", not(hasItem("wrong-role@mem-a.test"))))
                .andExpect(jsonPath("$[*].email", not(hasItem("member@mem-a.test"))))
                .andExpect(jsonPath("$[*].email", not(hasItem("foreign.freelancer@mem-b.test"))));
    }

    @Test
    @DisplayName("DEFECT-MEM-01: Owning Client omitted keyword succeeds on PostgreSQL")
    void owningClientOmittedKeyword_ShouldReturnScopedResults() throws Exception {
        performSearch(owner, TENANT_A, project.getId(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("DEFECT-MEM-01: Blank keyword follows the documented omitted interpretation")
    void blankKeyword_ShouldReturnSameScopedPopulationAsOmitted() throws Exception {
        performSearch(administrator, TENANT_A, project.getId(), "   ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].email", containsInAnyOrder(
                        "alice.freelancer@mem-a.test",
                        "bob.freelancer@mem-a.test")));
    }

    @Test
    @DisplayName("FR04: Keyword matching is case-insensitive and no-match is empty")
    void keyword_ShouldMatchCaseInsensitivelyAndReturnEmptyWhenAbsent() throws Exception {
        performSearch(owner, TENANT_A, project.getId(), "aLiCe")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value("alice.freelancer@mem-a.test"));

        performSearch(owner, TENANT_A, project.getId(), "no-such-freelancer")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("FR04: Same-tenant non-owner Client is denied")
    void sameTenantOutsider_ShouldBeDenied() throws Exception {
        performSearch(outsiderClient, TENANT_A, project.getId(), null)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR04: Cross-tenant project and Freelancer data remain non-disclosing")
    void crossTenantActor_ShouldReceiveNotFoundWithoutForeignFreelancers() throws Exception {
        performSearch(foreignAdministrator, TENANT_B, project.getId(), null)
                .andExpect(status().isNotFound())
                .andExpect(MockMvcResultMatchers.content().string(
                        not(org.hamcrest.Matchers.containsString("alice.freelancer@mem-a.test"))));
    }

    @Test
    @DisplayName("FR04: Search result limit remains capped at twenty")
    void omittedKeyword_ShouldPreserveTwentyResultLimit() throws Exception {
        TenantContext.setTenantId(TENANT_A);
        for (int index = 0; index < 25; index++) {
            createUser(
                    TENANT_A,
                    "candidate-" + index + "@mem-a.test",
                    "Candidate " + String.format("%02d", index),
                    Role.FREELANCER,
                    true);
        }
        TenantContext.clear();

        performSearch(administrator, TENANT_A, project.getId(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(20)));
    }

    @Test
    @DisplayName("MEM-03: Cross-tenant membership target is a non-disclosing 404")
    void crossTenantMembershipTarget_ShouldReturnNotFound() throws Exception {
        CustomUserDetails userDetails = CustomUserDetails.build(owner);
        var auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());

        mockMvc.perform(post("/api/projects/{projectId}/members", project.getId())
                        .header("X-Tenant-ID", TENANT_A)
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + foreignFreelancer.getId() + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(MockMvcResultMatchers.content().string(
                        not(org.hamcrest.Matchers.containsString(
                                "foreign.freelancer@mem-b.test"))));
    }

    private org.springframework.test.web.servlet.ResultActions performSearch(
            User actor,
            String tenantId,
            UUID projectId,
            String keyword
    ) throws Exception {
        CustomUserDetails userDetails = CustomUserDetails.build(actor);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        MockHttpServletRequestBuilder request = get(
                "/api/projects/{projectId}/freelancers/search", projectId)
                .header("X-Tenant-ID", tenantId)
                .with(authentication(auth));
        if (keyword != null) {
            request.param("keyword", keyword);
        }
        return mockMvc.perform(request);
    }

    private User createUser(String tenantId, String email, String fullName, Role role, boolean active) {
        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(email);
        user.setPassword("hashed-password");
        user.setFullName(fullName);
        user.setRole(role);
        user.setActive(active);
        return userRepository.saveAndFlush(user);
    }

    private Project createProject(String tenantId, String title, User projectOwner) {
        Project created = new Project();
        created.setTenantId(tenantId);
        created.setTitle(title);
        created.setDescription("Test fixture");
        created.setBudget(BigDecimal.TEN);
        created.setStatus(ProjectStatus.IN_PROGRESS);
        created.setOwner(projectOwner);
        return projectRepository.saveAndFlush(created);
    }

    private void addMember(Project memberProject, User member) {
        ProjectMember projectMember = new ProjectMember();
        projectMember.setId(new ProjectMemberId(memberProject.getId(), member.getId()));
        projectMember.setProject(memberProject);
        projectMember.setUser(member);
        projectMember.setTenantId(memberProject.getTenantId());
        projectMemberRepository.saveAndFlush(projectMember);
    }

    private void cleanFixtureData() {
        jdbcTemplate.update(
                "DELETE FROM project_members WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
        jdbcTemplate.update(
                "DELETE FROM projects WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
        jdbcTemplate.update(
                "DELETE FROM users WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
    }
}
