package com.clienthub.core.dto.project;

import com.clienthub.core.domain.enums.ProjectStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ProjectRequest {

    @NotBlank
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    private String description;

    @PositiveOrZero(message = "Budget must be positive")
    private BigDecimal budget;

    @FutureOrPresent(message = "Deadline cannot be in the past")
    private LocalDate deadline;

    private ProjectStatus status;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getBudget() {
        return budget;
    }

    public void setBudget(BigDecimal budget) {
        this.budget = budget;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDate deadline) {
        this.deadline = deadline;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public void setStatus(ProjectStatus status) {
        this.status = status;
    }
}
