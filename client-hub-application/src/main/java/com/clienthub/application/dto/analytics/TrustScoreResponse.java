package com.clienthub.application.dto.analytics;

public record TrustScoreResponse(
    int trustScore,
    long paidInvoices,
    long totalInvoices,
    String calculatedFrom
) {}
