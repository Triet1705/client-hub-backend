package com.clienthub.core.domain.entity;

import com.clienthub.common.domain.BaseEntity;
import com.clienthub.core.domain.enums.TaskPriority;
import com.clienthub.core.domain.enums.TaskStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tasks")
@SQLDelete(sql = "UPDATE tasks SET is_deleted = true, deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Task extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 200)
    @Size(min = 3, max = 200, message = "Title must be between 3 and 200 characters")
    private String title;

    @Size(max = 5000, message = "Description too long")
    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private TaskStatus status = TaskStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Column(nullable = false)
    @Min(value = 0, message = "Estimated hours cannot be negative")
    private Integer estimatedHours;

    @Column(name = "actual_hours")
    private Integer actualHours;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public Task() {
    }

    public Task(UUID id, String title, String description, Project project, User assignedTo,
                TaskStatus status, TaskPriority priority, Integer estimatedHours,
                Integer actualHours, LocalDateTime dueDate, String tenantId,
                boolean isDeleted, LocalDateTime deletedAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.project = project;
        this.assignedTo = assignedTo;
        this.status = status != null ? status : TaskStatus.TODO;
        this.priority = priority != null ? priority : TaskPriority.MEDIUM;
        this.estimatedHours = estimatedHours;
        this.actualHours = actualHours;
        this.dueDate = dueDate;
        this.tenantId = tenantId;
        this.isDeleted = isDeleted;
        this.deletedAt = deletedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    public User getAssignedTo() { return assignedTo; }
    public void setAssignedTo(User assignedTo) { this.assignedTo = assignedTo; }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }

    public Integer getEstimatedHours() { return estimatedHours; }
    public void setEstimatedHours(Integer estimatedHours) { this.estimatedHours = estimatedHours; }

    public Integer getActualHours() { return actualHours; }
    public void setActualHours(Integer actualHours) { this.actualHours = actualHours; }

    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    public static TaskBuilder builder() {
        return new TaskBuilder();
    }

    public static class TaskBuilder {
        private UUID id;
        private String title;
        private String description;
        private Project project;
        private User assignedTo;
        private TaskStatus status = TaskStatus.TODO;
        private TaskPriority priority = TaskPriority.MEDIUM;
        private Integer estimatedHours;
        private Integer actualHours;
        private LocalDateTime dueDate;
        private String tenantId;
        private boolean isDeleted = false;
        private LocalDateTime deletedAt;

        TaskBuilder() { }

        public TaskBuilder id(UUID id) { this.id = id; return this; }
        public TaskBuilder title(String title) { this.title = title; return this; }
        public TaskBuilder description(String description) { this.description = description; return this; }
        public TaskBuilder project(Project project) { this.project = project; return this; }
        public TaskBuilder assignedTo(User assignedTo) { this.assignedTo = assignedTo; return this; }
        public TaskBuilder status(TaskStatus status) { this.status = status; return this; }
        public TaskBuilder priority(TaskPriority priority) { this.priority = priority; return this; }
        public TaskBuilder estimatedHours(Integer estimatedHours) { this.estimatedHours = estimatedHours; return this; }
        public TaskBuilder actualHours(Integer actualHours) { this.actualHours = actualHours; return this; }
        public TaskBuilder dueDate(LocalDateTime dueDate) { this.dueDate = dueDate; return this; }
        public TaskBuilder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public TaskBuilder isDeleted(boolean isDeleted) { this.isDeleted = isDeleted; return this; }
        public TaskBuilder deletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; return this; }

        public Task build() {
            return new Task(id, title, description, project, assignedTo, status, priority, estimatedHours, actualHours, dueDate, tenantId, isDeleted, deletedAt);
        }
    }
}