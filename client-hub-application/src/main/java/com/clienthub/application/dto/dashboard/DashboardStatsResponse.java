package com.clienthub.application.dto.dashboard;

import java.math.BigDecimal;

public class DashboardStatsResponse {
    private long activeProjects;
    private long pendingTasks;
    private BigDecimal awaitingPaymentAmount;
    private BigDecimal escrowLocked;

    public DashboardStatsResponse(long activeProjects, long pendingTasks, BigDecimal awaitingPaymentAmount, BigDecimal escrowLocked) {
        this.activeProjects = activeProjects;
        this.pendingTasks = pendingTasks;
        this.awaitingPaymentAmount = awaitingPaymentAmount;
        this.escrowLocked = escrowLocked;
    }

    public long getActiveProjects() { return activeProjects; }
    public void setActiveProjects(long activeProjects) { this.activeProjects = activeProjects; }

    public long getPendingTasks() { return pendingTasks; }
    public void setPendingTasks(long pendingTasks) { this.pendingTasks = pendingTasks; }

    public BigDecimal getAwaitingPaymentAmount() { return awaitingPaymentAmount; }
    public void setAwaitingPaymentAmount(BigDecimal awaitingPaymentAmount) { this.awaitingPaymentAmount = awaitingPaymentAmount; }

    public BigDecimal getEscrowLocked() { return escrowLocked; }
    public void setEscrowLocked(BigDecimal escrowLocked) { this.escrowLocked = escrowLocked; }
}