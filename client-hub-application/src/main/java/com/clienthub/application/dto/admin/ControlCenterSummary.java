package com.clienthub.application.dto.admin;

import java.math.BigDecimal;

public record ControlCenterSummary(
        BigDecimal totalRevenue,
        long activeUsers24h,
        long openProjects,
        long unpaidInvoices,
        String systemStatus
) {}
