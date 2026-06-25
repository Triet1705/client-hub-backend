package com.clienthub.application.dto.admin;

import java.util.List;

public record AdminControlCenterResponse(
        ControlCenterSummary summary,
        AdminHealthResponse health,
        List<OperationalAlert> alerts,
        List<AdminEventItem> recentEvents,
        List<AdminAuditLogResponse> recentAuditLogs,
        List<AdminFeatureFlag> flags
) {}
