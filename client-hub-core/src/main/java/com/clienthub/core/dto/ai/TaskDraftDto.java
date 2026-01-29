package com.clienthub.core.dto.ai;

import com.clienthub.core.domain.enums.TaskPriority;
import java.time.LocalDate;

public class TaskDraftDto {
    private String title;
    private String description;
    private TaskPriority priority;
    private Integer estimatedHours;
    private LocalDate dueDate;
    private double confidenceScore;

    public TaskDraftDto() {}

    public TaskDraftDto(String title, String description, TaskPriority priority,
                        Integer estimatedHours, LocalDate dueDate, double confidenceScore) {
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.estimatedHours = estimatedHours;
        this.dueDate = dueDate;
        this.confidenceScore = confidenceScore;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }

    public Integer getEstimatedHours() { return estimatedHours; }
    public void setEstimatedHours(Integer estimatedHours) { this.estimatedHours = estimatedHours; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }
}