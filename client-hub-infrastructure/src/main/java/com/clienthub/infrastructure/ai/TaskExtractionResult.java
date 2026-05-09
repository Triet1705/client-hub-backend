package com.clienthub.infrastructure.ai;

import java.util.List;

public class TaskExtractionResult {
    private String documentSummary;
    private double overallConfidence;
    private boolean reviewPassTriggered;
    private long processingTimeMs;
    private List<TaskDraftDto> tasks;

    public TaskExtractionResult() {
    }

    public TaskExtractionResult(String documentSummary, double overallConfidence, boolean reviewPassTriggered, long processingTimeMs, List<TaskDraftDto> tasks) {
        this.documentSummary = documentSummary;
        this.overallConfidence = overallConfidence;
        this.reviewPassTriggered = reviewPassTriggered;
        this.processingTimeMs = processingTimeMs;
        this.tasks = tasks;
    }

    public static TaskExtractionResult fallback(String reason) {
        TaskDraftDto fallbackTask = new TaskDraftDto(
                "Draft Task (AI Unavailable)",
                reason,
                com.clienthub.domain.enums.TaskPriority.LOW,
                0,
                null,
                0.0
        );
        return new TaskExtractionResult(
                reason,
                0.0,
                false,
                0,
                List.of(fallbackTask)
        );
    }

    public String getDocumentSummary() {
        return documentSummary;
    }

    public void setDocumentSummary(String documentSummary) {
        this.documentSummary = documentSummary;
    }

    public double getOverallConfidence() {
        return overallConfidence;
    }

    public void setOverallConfidence(double overallConfidence) {
        this.overallConfidence = overallConfidence;
    }

    public boolean isReviewPassTriggered() {
        return reviewPassTriggered;
    }

    public void setReviewPassTriggered(boolean reviewPassTriggered) {
        this.reviewPassTriggered = reviewPassTriggered;
    }

    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public List<TaskDraftDto> getTasks() {
        return tasks;
    }

    public void setTasks(List<TaskDraftDto> tasks) {
        this.tasks = tasks;
    }
}
