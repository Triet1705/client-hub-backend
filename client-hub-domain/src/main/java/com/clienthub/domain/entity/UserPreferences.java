package com.clienthub.domain.entity;

import com.clienthub.common.domain.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "user_preferences", indexes = {
        @Index(name = "idx_user_preferences_tenant_user", columnList = "tenant_id, user_id", unique = true)
})
public class UserPreferences extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "theme", nullable = false, length = 20)
    private String theme = "dark";

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "date_format", nullable = false, length = 30)
    private String dateFormat = "DD/MM/YYYY";

    @Column(name = "timezone", nullable = false, length = 80)
    private String timezone = "UTC";

    @Column(name = "notify_comments")
    private boolean notifyComments = true;

    @Column(name = "notify_tasks")
    private boolean notifyTasks = true;

    @Column(name = "notify_projects")
    private boolean notifyProjects = true;

    @Column(name = "notify_invoices")
    private boolean notifyInvoices = true;

    @Column(name = "quiet_hours_enabled")
    private boolean quietHoursEnabled = false;

    @Column(name = "quiet_hours_start", length = 5)
    private String quietHoursStart;

    @Column(name = "quiet_hours_end", length = 5)
    private String quietHoursEnd;

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getDateFormat() { return dateFormat; }
    public void setDateFormat(String dateFormat) { this.dateFormat = dateFormat; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public boolean isNotifyComments() { return notifyComments; }
    public void setNotifyComments(boolean notifyComments) { this.notifyComments = notifyComments; }
    public boolean isNotifyTasks() { return notifyTasks; }
    public void setNotifyTasks(boolean notifyTasks) { this.notifyTasks = notifyTasks; }
    public boolean isNotifyProjects() { return notifyProjects; }
    public void setNotifyProjects(boolean notifyProjects) { this.notifyProjects = notifyProjects; }
    public boolean isNotifyInvoices() { return notifyInvoices; }
    public void setNotifyInvoices(boolean notifyInvoices) { this.notifyInvoices = notifyInvoices; }
    public boolean isQuietHoursEnabled() { return quietHoursEnabled; }
    public void setQuietHoursEnabled(boolean quietHoursEnabled) { this.quietHoursEnabled = quietHoursEnabled; }
    public String getQuietHoursStart() { return quietHoursStart; }
    public void setQuietHoursStart(String quietHoursStart) { this.quietHoursStart = quietHoursStart; }
    public String getQuietHoursEnd() { return quietHoursEnd; }
    public void setQuietHoursEnd(String quietHoursEnd) { this.quietHoursEnd = quietHoursEnd; }
}
