package com.clienthub.application.dto.analytics;

public record ResponseRateResponse(
    int responseRate,
    long respondedThreads,
    long totalThreads,
    String unit
) {}
