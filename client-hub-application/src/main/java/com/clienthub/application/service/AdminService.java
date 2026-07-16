package com.clienthub.application.service;

import com.clienthub.application.dto.admin.AdminAuditLogResponse;
import com.clienthub.application.dto.admin.AdminControlCenterResponse;
import com.clienthub.application.dto.admin.AdminEventItem;
import com.clienthub.application.dto.admin.AdminFeatureFlag;
import com.clienthub.application.dto.admin.AdminHealthResponse;
import com.clienthub.application.dto.admin.AdminInvoiceResponse;
import com.clienthub.application.dto.admin.AdminProjectResponse;
import com.clienthub.application.dto.admin.AdminUserDetailResponse;
import com.clienthub.application.dto.admin.AdminUserResponse;
import com.clienthub.application.dto.admin.ComponentHealth;
import com.clienthub.application.dto.admin.ControlCenterSummary;
import com.clienthub.application.dto.admin.ImpersonationResponse;
import com.clienthub.application.dto.admin.JvmVitals;
import com.clienthub.application.dto.admin.OperationalAlert;
import com.clienthub.application.dto.analytics.AdminDashboardResponse;
import com.clienthub.domain.entity.AuditLog;
import com.clienthub.domain.entity.AuditAnchorMember;
import com.clienthub.domain.entity.Invoice;
import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.AuditAction;
import com.clienthub.domain.enums.AuditAnchorBatchStatus;
import com.clienthub.domain.enums.AuditRecordAnchorStatus;
import com.clienthub.domain.enums.InvoiceStatus;
import com.clienthub.domain.enums.ProjectStatus;
import com.clienthub.domain.enums.Role;
import com.clienthub.domain.repository.AuditLogRepository;
import com.clienthub.domain.repository.AuditAnchorMemberRepository;
import com.clienthub.domain.repository.InvoiceRepository;
import com.clienthub.domain.repository.ProjectRepository;
import com.clienthub.domain.repository.UserRepository;
import com.clienthub.infrastructure.security.JwtTokenProvider;
import jakarta.persistence.criteria.Predicate;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
@Transactional(readOnly = true)
public class AdminService {

    private static final Logger logger = LoggerFactory.getLogger(AdminService.class);
    private static final List<ProjectStatus> CLOSED_PROJECT_STATUSES =
            List.of(ProjectStatus.COMPLETED, ProjectStatus.CANCELLED);
    private static final List<InvoiceStatus> PAID_OR_REFUNDED_INVOICE_STATUSES =
            List.of(InvoiceStatus.PAID, InvoiceStatus.REFUNDED);
    private static final List<AuditAction> AUTH_ACTIONS =
            List.of(AuditAction.LOGIN, AuditAction.LOGIN_FAILED, AuditAction.LOGOUT,
                    AuditAction.USER_LOCKED, AuditAction.USER_UNLOCKED, AuditAction.ADMIN_IMPERSONATION);
    private static final List<AuditAction> WEB3_ACTIONS =
            List.of(AuditAction.ANCHOR_SUCCESS, AuditAction.ANCHOR_FAILED);
    private static final List<AuditAction> INVOICE_ACTIONS =
            List.of(AuditAction.INVOICE_SENT, AuditAction.INVOICE_PAID, AuditAction.INVOICE_CANCELLED);
    private static final List<AuditAction> CRITICAL_ACTIONS =
            List.of(AuditAction.LOGIN_FAILED, AuditAction.USER_LOCKED, AuditAction.ANCHOR_FAILED);
    private static final List<AuditAction> WARNING_ACTIONS =
            List.of(AuditAction.DELETE, AuditAction.DISPUTE_OPENED, AuditAction.INVOICE_CANCELLED);
    private static final List<AuditAction> SUCCESS_ACTIONS =
            List.of(AuditAction.CREATE, AuditAction.INVOICE_PAID, AuditAction.ANCHOR_SUCCESS);
    private static final List<String> KNOWN_ENTITY_CATEGORIES =
            List.of("INVOICE", "PROJECT", "TASK", "USER", "SYSTEM");

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final InvoiceRepository invoiceRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditAnchorMemberRepository auditAnchorMemberRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate restTemplate;

    @Value("${ai.ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${ai.ollama.model:llama3.2:3b}")
    private String ollamaModel;

    @Value("${blockchain.enabled:false}")
    private boolean blockchainEnabled;

    @Value("${blockchain.node_url:}")
    private String blockchainNodeUrl;

    @Value("${blockchain.contract_address:}")
    private String blockchainContractAddress;

    @Value("${audit.anchor.contract_address:}")
    private String auditAnchorContractAddress;

    @Value("${app.tenant.require-header:false}")
    private boolean tenantHeaderRequired;

    @Value("${app.rls.enabled:false}")
    private boolean rlsEnabled;

    @Value("${rate-limit.login:0}")
    private int loginRateLimit;

    @Value("${rate-limit.register:0}")
    private int registerRateLimit;

    public AdminService(UserRepository userRepository,
                        ProjectRepository projectRepository,
                        InvoiceRepository invoiceRepository,
                        AuditLogRepository auditLogRepository,
                        AuditAnchorMemberRepository auditAnchorMemberRepository,
                        JwtTokenProvider jwtTokenProvider,
                        JdbcTemplate jdbcTemplate,
                        RedisTemplate<String, Object> redisTemplate,
                        RestTemplateBuilder restTemplateBuilder) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.invoiceRepository = invoiceRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditAnchorMemberRepository = auditAnchorMemberRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplateBuilder.setConnectTimeout(Duration.ofSeconds(2))
                                               .setReadTimeout(Duration.ofSeconds(5))
                                               .build();
    }

    public Page<AdminUserResponse> listUsers(Role role, Boolean active, String keyword, Pageable pageable) {
        Specification<User> spec = Specification.where(null);

        if (role != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("role"), role));
        }
        if (active != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }
        if (keyword != null && !keyword.isBlank()) {
            String likePattern = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("email")), likePattern),
                    cb.like(cb.lower(root.get("fullName")), likePattern)
            ));
        }

        return userRepository.findAll(spec, pageable).map(AdminUserResponse::from);
    }

    public AdminUserDetailResponse getUserDetail(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        return AdminUserDetailResponse.from(user, 0L, 0L);
    }

    @Transactional
    public void updateUserStatus(UUID userId, boolean active) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setActive(active);
        userRepository.save(user);
    }

    @Transactional
    public void updateUserRole(UUID userId, Role role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        if (user.getRole() == Role.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Cannot change role of an ADMIN user.");
        }

        user.setRole(role);
        userRepository.save(user);
    }

    public AdminDashboardResponse getPlatformAnalytics() {
        long totalUsers = userRepository.count();
        long totalProjects = projectRepository.count();
        long totalInvoices = invoiceRepository.count();
        BigDecimal totalRevenue = invoiceRepository.sumAmountByStatuses(List.of(InvoiceStatus.PAID));

        return new AdminDashboardResponse(totalUsers, totalProjects, totalInvoices, totalRevenue, "Operational");
    }

    public AdminControlCenterResponse getControlCenter() {
        AdminHealthResponse health = getSystemHealth();
        ControlCenterSummary summary = buildSummary(health.overallStatus());
        List<AdminFeatureFlag> flags = getFeatureFlags();
        List<OperationalAlert> alerts = buildOperationalAlerts(health, summary);
        List<AdminEventItem> recentEvents = listEvents(null, null, null, null, null, null, Pageable.ofSize(8))
                .getContent();
        List<AdminAuditLogResponse> recentAuditLogs = listRecentActivity(Pageable.ofSize(8))
                .getContent();

        return new AdminControlCenterResponse(summary, health, alerts, recentEvents, recentAuditLogs, flags);
    }

    private ControlCenterSummary buildSummary(String systemStatus) {
        return new ControlCenterSummary(
                invoiceRepository.sumAmountByStatuses(List.of(InvoiceStatus.PAID)),
                userRepository.countByLastLoginAtAfter(Instant.now().minus(Duration.ofHours(24))),
                projectRepository.countByStatusNotIn(CLOSED_PROJECT_STATUSES),
                invoiceRepository.countByStatusNotIn(PAID_OR_REFUNDED_INVOICE_STATUSES),
                systemStatus);
    }

    public Page<AdminProjectResponse> listAllProjects(Pageable pageable) {
        return projectRepository.findAll(pageable)
                .map(p -> AdminProjectResponse.from(p, 0L, 0L));
    }

    public Page<AdminInvoiceResponse> listAllInvoices(Pageable pageable) {
        return invoiceRepository.findAll(pageable)
                .map(AdminInvoiceResponse::from);
    }

    @Transactional
    public void forceInvoiceStatus(Long invoiceId, InvoiceStatus status, String reason, UUID adminId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + invoiceId));

        logger.warn("AUDIT: Admin {} forced status of Invoice {} to {}. Reason: {}", adminId, invoiceId, status, reason);

        invoice.setStatus(status);
        invoiceRepository.save(invoice);
    }

    public ImpersonationResponse impersonate(UUID targetUserId, UUID adminId) {
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + targetUserId));

        if (targetUser.getRole() == Role.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Cannot impersonate another ADMIN user.");
        }

        String token = jwtTokenProvider.generateImpersonationToken(
                targetUser.getId(),
                targetUser.getEmail(),
                targetUser.getRole().name(),
                targetUser.getTenantId(),
                adminId
        );

        return new ImpersonationResponse(
                token,
                targetUser.getId(),
                targetUser.getEmail(),
                targetUser.getRole(),
                targetUser.getTenantId(),
                true
        );
    }

    public Page<AdminAuditLogResponse> listRecentActivity(Pageable pageable) {
        return listRecentActivity(null, null, null, null, null, null, null, pageable);
    }

    public Page<AdminAuditLogResponse> listRecentActivity(
            AuditAction action,
            String entityType,
            String tenantId,
            Boolean anchored,
            AuditRecordAnchorStatus anchorStatus,
            Instant from,
            Instant to,
            Pageable pageable) {
        return auditLogRepository.findAll(
                        buildAuditLogSpec(action, entityType, tenantId, anchored, anchorStatus, from, to),
                        pageable)
                .map(log -> AdminAuditLogResponse.from(log, isConfirmedAnchor(log.getId())));
    }

    public Page<AdminEventItem> listEvents(
            String category,
            String severity,
            String entityType,
            String tenantId,
            Instant from,
            Instant to,
            Pageable pageable) {
        String normalizedCategory = normalizeFilter(category);
        String normalizedSeverity = normalizeFilter(severity);
        return auditLogRepository.findAll(
                        buildEventLogSpec(normalizedCategory, normalizedSeverity, entityType, tenantId, from, to),
                        pageable)
                .map(this::toEventItem);
    }

    public List<AdminFeatureFlag> getFeatureFlags() {
        boolean blockchainConfigured = isBlockchainConfigured();
        boolean rateLimitingEnabled = loginRateLimit > 0 && registerRateLimit > 0;

        return List.of(
                new AdminFeatureFlag(
                        "blockchain-workflows",
                        "Blockchain workflows",
                        blockchainEnabled,
                        blockchainEnabled ? (blockchainConfigured ? "READY" : "NEEDS_CONFIG") : "DISABLED",
                        "Controls Web3 escrow and certificate workflows.",
                        "blockchain.enabled"),
                new AdminFeatureFlag(
                        "ai-extraction",
                        "AI extraction",
                        isConfigured(ollamaUrl),
                        isConfigured(ollamaUrl) ? "CONFIGURED" : "DISABLED",
                        "Enables smart task extraction through Ollama model " + ollamaModel + ".",
                        "ai.ollama.url"),
                new AdminFeatureFlag(
                        "tenant-header-required",
                        "Tenant header enforcement",
                        tenantHeaderRequired,
                        tenantHeaderRequired ? "ENFORCED" : "DEV_FALLBACK",
                        "Requires X-Tenant-ID for tenant-scoped requests.",
                        "app.tenant.require-header"),
                new AdminFeatureFlag(
                        "row-level-security",
                        "Row-level security",
                        rlsEnabled,
                        rlsEnabled ? "ENFORCED" : "DISABLED",
                        "Applies PostgreSQL tenant policy enforcement when enabled.",
                        "app.rls.enabled"),
                new AdminFeatureFlag(
                        "auth-rate-limits",
                        "Auth rate limits",
                        rateLimitingEnabled,
                        rateLimitingEnabled ? "ACTIVE" : "DISABLED",
                        "Login limit " + loginRateLimit + ", register limit " + registerRateLimit + ".",
                        "rate-limit.login/register"),
                new AdminFeatureFlag(
                        "admin-impersonation",
                        "Admin impersonation",
                        true,
                        "ACTIVE",
                        "Allows ADMIN users to generate audited impersonation sessions.",
                        "admin endpoint"),
                new AdminFeatureFlag(
                        "audit-anchoring",
                        "Audit anchoring readiness",
                        blockchainConfigured && isConfigured(auditAnchorContractAddress),
                        blockchainConfigured && isConfigured(auditAnchorContractAddress) ? "READY" : "NOT_READY",
                        "Requires blockchain workflow config before audit roots can be anchored.",
                        "audit.anchor.contract_address"));
    }

    public AdminHealthResponse getSystemHealth() {
        ComponentHealth dbHealth = checkDatabase();
        ComponentHealth redisHealth = checkRedis();
        ComponentHealth aiHealth = checkAiEngine();
        ComponentHealth blockchainHealth = checkBlockchainConfig();

        String overallStatus = deriveOverallStatus(dbHealth, redisHealth, aiHealth, blockchainHealth);

        return new AdminHealthResponse(
                overallStatus,
                dbHealth,
                redisHealth,
                aiHealth,
                blockchainHealth,
                JvmVitals.current(),
                ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
                Instant.now());
    }

    private Specification<AuditLog> buildAuditLogSpec(
            AuditAction action,
            String entityType,
            String tenantId,
            Boolean anchored,
            AuditRecordAnchorStatus anchorStatus,
            Instant from,
            Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (isConfigured(entityType)) {
                predicates.add(cb.equal(cb.upper(root.get("entityType")), entityType.trim().toUpperCase(Locale.ROOT)));
            }
            if (isConfigured(tenantId)) {
                predicates.add(cb.equal(root.get("tenantId"), tenantId.trim()));
            }
            if (anchorStatus != null) {
                var subquery = query.subquery(Long.class);
                var member = subquery.from(AuditAnchorMember.class);
                List<jakarta.persistence.criteria.Predicate> memberPredicates = new ArrayList<>();
                memberPredicates.add(cb.equal(member.get("auditLogId"), root.get("id")));
                if (anchorStatus == AuditRecordAnchorStatus.PENDING) {
                    memberPredicates.add(member.get("batch").get("status").in(
                            AuditAnchorBatchStatus.BUILDING,
                            AuditAnchorBatchStatus.READY,
                            AuditAnchorBatchStatus.SUBMITTED));
                } else if (anchorStatus == AuditRecordAnchorStatus.VERIFIED) {
                    memberPredicates.add(cb.equal(member.get("batch").get("status"), AuditAnchorBatchStatus.CONFIRMED));
                } else if (anchorStatus == AuditRecordAnchorStatus.FAILED) {
                    memberPredicates.add(cb.equal(member.get("batch").get("status"), AuditAnchorBatchStatus.FAILED));
                }
                subquery.select(member.get("id")).where(memberPredicates.toArray(Predicate[]::new));
                predicates.add(anchorStatus == AuditRecordAnchorStatus.WAITING
                        ? cb.not(cb.exists(subquery))
                        : cb.exists(subquery));
            } else if (anchored != null) {
                var subquery = query.subquery(Long.class);
                var member = subquery.from(AuditAnchorMember.class);
                subquery.select(member.get("id")).where(
                        cb.equal(member.get("auditLogId"), root.get("id")),
                        cb.equal(member.get("batch").get("status"), AuditAnchorBatchStatus.CONFIRMED));
                predicates.add(anchored ? cb.exists(subquery) : cb.not(cb.exists(subquery)));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<AuditLog> buildEventLogSpec(
            String category,
            String severity,
            String entityType,
            String tenantId,
            Instant from,
            Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (isConfigured(entityType)) {
                predicates.add(cb.equal(cb.upper(root.get("entityType")), entityType.trim().toUpperCase(Locale.ROOT)));
            }
            if (isConfigured(tenantId)) {
                predicates.add(cb.equal(root.get("tenantId"), tenantId.trim()));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            addEventCategoryPredicate(predicates, root, cb, category);
            addEventSeverityPredicate(predicates, root, cb, severity);
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void addEventCategoryPredicate(
            List<Predicate> predicates,
            jakarta.persistence.criteria.Root<AuditLog> root,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            String category) {
        if (!isConfigured(category)) {
            return;
        }
        switch (category) {
            case "AUTH" -> predicates.add(root.get("action").in(AUTH_ACTIONS));
            case "WEB3" -> predicates.add(root.get("action").in(WEB3_ACTIONS));
            case "INVOICE" -> predicates.add(cb.or(
                    root.get("action").in(INVOICE_ACTIONS),
                    cb.equal(cb.upper(root.get("entityType")), "INVOICE")));
            case "PROJECT", "TASK", "SYSTEM" ->
                    predicates.add(cb.equal(cb.upper(root.get("entityType")), category));
            case "USER" -> predicates.add(cb.and(
                    cb.equal(cb.upper(root.get("entityType")), "USER"),
                    cb.not(root.get("action").in(AUTH_ACTIONS))));
            case "AUDIT" -> predicates.add(cb.and(
                    cb.not(root.get("action").in(AUTH_ACTIONS)),
                    cb.not(root.get("action").in(WEB3_ACTIONS)),
                    cb.not(root.get("action").in(INVOICE_ACTIONS)),
                    cb.not(cb.upper(root.get("entityType")).in(KNOWN_ENTITY_CATEGORIES))));
            default -> predicates.add(cb.disjunction());
        }
    }

    private void addEventSeverityPredicate(
            List<Predicate> predicates,
            jakarta.persistence.criteria.Root<AuditLog> root,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            String severity) {
        if (!isConfigured(severity)) {
            return;
        }
        switch (severity) {
            case "CRITICAL" -> predicates.add(root.get("action").in(CRITICAL_ACTIONS));
            case "WARNING" -> predicates.add(root.get("action").in(WARNING_ACTIONS));
            case "SUCCESS" -> predicates.add(root.get("action").in(SUCCESS_ACTIONS));
            case "INFO" -> predicates.add(cb.and(
                    cb.not(root.get("action").in(CRITICAL_ACTIONS)),
                    cb.not(root.get("action").in(WARNING_ACTIONS)),
                    cb.not(root.get("action").in(SUCCESS_ACTIONS))));
            default -> predicates.add(cb.disjunction());
        }
    }

    private AdminEventItem toEventItem(AuditLog log) {
        String action = log.getAction() != null ? log.getAction().name() : "UNKNOWN";
        String entityType = isConfigured(log.getEntityType()) ? log.getEntityType() : "SYSTEM";
        String category = deriveEventCategory(log);
        String severity = deriveEventSeverity(log);
        String actor = isConfigured(log.getUserEmail()) ? log.getUserEmail() : "System";

        return new AdminEventItem(
                log.getId(),
                category,
                severity,
                humanize(action),
                actor + " performed " + humanize(action).toLowerCase(Locale.ROOT)
                        + " on " + entityType.toLowerCase(Locale.ROOT)
                        + (isConfigured(log.getEntityId()) ? " " + log.getEntityId() : ""),
                log.getUserEmail(),
                log.getTenantId(),
                log.getEntityType(),
                log.getEntityId(),
                log.getCreatedAt());
    }

    private String deriveEventCategory(AuditLog log) {
        AuditAction action = log.getAction();
        String entityType = log.getEntityType() != null ? log.getEntityType().toUpperCase(Locale.ROOT) : "";
        if (action == null) {
            return "AUDIT";
        }
        if (AUTH_ACTIONS.contains(action)) {
            return "AUTH";
        }
        if (action.name().startsWith("ANCHOR")) {
            return "WEB3";
        }
        if (action.name().startsWith("INVOICE") || "INVOICE".equals(entityType)) {
            return "INVOICE";
        }
        if ("PROJECT".equals(entityType)) {
            return "PROJECT";
        }
        if ("TASK".equals(entityType)) {
            return "TASK";
        }
        if ("USER".equals(entityType)) {
            return "USER";
        }
        if ("SYSTEM".equals(entityType)) {
            return "SYSTEM";
        }
        return "AUDIT";
    }

    private String deriveEventSeverity(AuditLog log) {
        AuditAction action = log.getAction();
        if (action == null) {
            return "INFO";
        }
        return switch (action) {
            case LOGIN_FAILED, USER_LOCKED, ANCHOR_FAILED -> "CRITICAL";
            case DELETE, DISPUTE_OPENED, INVOICE_CANCELLED -> "WARNING";
            case CREATE, INVOICE_PAID, ANCHOR_SUCCESS -> "SUCCESS";
            default -> "INFO";
        };
    }

    private List<OperationalAlert> buildOperationalAlerts(AdminHealthResponse health, ControlCenterSummary summary) {
        Instant now = Instant.now();
        List<OperationalAlert> alerts = new ArrayList<>();
        addHealthAlert(alerts, "database", "Database", health.database(), now);
        addHealthAlert(alerts, "redis", "Redis", health.redis(), now);
        addHealthAlert(alerts, "ai-engine", "AI Engine", health.aiEngine(), now);
        addHealthAlert(alerts, "blockchain", "Blockchain config", health.blockchain(), now);

        long failedLogins = auditLogRepository.countByActionAndCreatedAtAfter(
                AuditAction.LOGIN_FAILED,
                now.minus(Duration.ofHours(24)));
        if (failedLogins > 0) {
            alerts.add(new OperationalAlert(
                    "failed-logins-24h",
                    failedLogins > 10 ? "CRITICAL" : "WARNING",
                    "Failed logins detected",
                    failedLogins + " failed login events were recorded in the last 24 hours.",
                    "Review the audit feed and confirm rate limits are active.",
                    now));
        }

        long unanchoredLogs = auditLogRepository.countWithoutConfirmedAnchor(
                List.of(AuditAction.ANCHOR_SUCCESS, AuditAction.ANCHOR_FAILED),
                AuditAnchorBatchStatus.CONFIRMED);
        if (unanchoredLogs > 0) {
            alerts.add(new OperationalAlert(
                    "unanchored-audit-logs",
                    blockchainEnabled ? "WARNING" : "INFO",
                    "Audit logs waiting for anchoring",
                    unanchoredLogs + " audit log records are not anchored.",
                    "Check blockchain readiness before enabling anchoring workflows.",
                    now));
        }

        if (summary.unpaidInvoices() > 0) {
            alerts.add(new OperationalAlert(
                    "unpaid-invoices",
                    "INFO",
                    "Unpaid invoices need review",
                    summary.unpaidInvoices() + " invoices are still unpaid or active.",
                    "Open invoice operations to inspect overdue or disputed records.",
                    now));
        }

        return alerts;
    }

    private boolean isConfirmedAnchor(Long auditLogId) {
        return auditAnchorMemberRepository.findByAuditLogId(auditLogId)
                .map(member -> member.getBatch().getStatus() == AuditAnchorBatchStatus.CONFIRMED)
                .orElse(false);
    }

    private void addHealthAlert(List<OperationalAlert> alerts, String id, String label, ComponentHealth health, Instant now) {
        if ("UP".equals(health.status())) {
            return;
        }
        alerts.add(new OperationalAlert(
                "health-" + id,
                "DOWN".equals(health.status()) ? "CRITICAL" : "WARNING",
                label + " is " + health.status().toLowerCase(Locale.ROOT),
                health.label(),
                "Check service configuration and logs for " + label + ".",
                now));
    }

    private String deriveOverallStatus(ComponentHealth database, ComponentHealth redis, ComponentHealth ai, ComponentHealth blockchain) {
        if ("DOWN".equals(database.status()) || "DOWN".equals(redis.status())) {
            return "DOWN";
        }
        if (!"UP".equals(database.status())
                || !"UP".equals(redis.status())
                || !"UP".equals(ai.status())
                || !"UP".equals(blockchain.status())) {
            return "DEGRADED";
        }
        return "UP";
    }

    private ComponentHealth checkDatabase() {
        long start = System.currentTimeMillis();
        try {
            jdbcTemplate.execute("SELECT 1");
            long latency = System.currentTimeMillis() - start;
            if (latency > 500) {
                return new ComponentHealth("DEGRADED", "Active (" + latency + "ms)", latency);
            }
            return new ComponentHealth("UP", "Active (" + latency + "ms)", latency);
        } catch (Exception e) {
            return new ComponentHealth("DOWN", "Disconnected", System.currentTimeMillis() - start);
        }
    }

    private ComponentHealth checkRedis() {
        long start = System.currentTimeMillis();
        try {
            String pingResult = redisTemplate.getConnectionFactory().getConnection().ping();
            long latency = System.currentTimeMillis() - start;
            if (latency > 500) {
                return new ComponentHealth("DEGRADED", "Connected (" + latency + "ms)", latency);
            }
            return new ComponentHealth("UP", "Connected (" + pingResult + ", " + latency + "ms)", latency);
        } catch (Exception e) {
            return new ComponentHealth("DOWN", "Disconnected", System.currentTimeMillis() - start);
        }
    }

    private ComponentHealth checkAiEngine() {
        long start = System.currentTimeMillis();
        try {
            restTemplate.getForEntity(ollamaUrl + "/api/tags", String.class);
            long latency = System.currentTimeMillis() - start;
            if (latency > 5000) {
                return new ComponentHealth("DEGRADED", "Operational (" + latency + "ms)", latency);
            }
            return new ComponentHealth("UP", "Operational (" + latency + "ms)", latency);
        } catch (Exception e) {
            return new ComponentHealth("DOWN", "Offline", System.currentTimeMillis() - start);
        }
    }

    private ComponentHealth checkBlockchainConfig() {
        if (!blockchainEnabled) {
            return new ComponentHealth("UP", "Disabled", 0);
        }
        if (!isBlockchainConfigured()) {
            return new ComponentHealth("DEGRADED", "Enabled but missing node or contract", 0);
        }
        return new ComponentHealth("UP", "Configured", 0);
    }

    private boolean isBlockchainConfigured() {
        return blockchainEnabled && isConfigured(blockchainNodeUrl) && isConfigured(blockchainContractAddress);
    }

    private boolean isConfigured(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeFilter(String value) {
        return isConfigured(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String humanize(String value) {
        if (!isConfigured(value)) {
            return "Unknown";
        }
        String lower = value.replace('_', ' ').toLowerCase(Locale.ROOT);
        return lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
    }
}
