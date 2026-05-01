package com.clienthub.application.dto.analytics;

public record ProjectProgressResponse(
    int progressPercent,
    long completedTasks,
    long totalTasks
) {}
