package com.clienthub.application.dto.analytics;

import java.math.BigDecimal;

public record AdminDashboardResponse(
    long totalUsers,
    long totalProjects,
    long totalInvoices,
    BigDecimal totalRevenue,
    String systemHealth
) {}
