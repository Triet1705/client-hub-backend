package com.clienthub.application.dto.task;

public class TaskSummaryResponse {

    private long todo;
    private long inProgress;
    private long done;
    private long total;

    public TaskSummaryResponse(long todo, long inProgress, long done) {
        this.todo = todo;
        this.inProgress = inProgress;
        this.done = done;
        this.total = todo + inProgress + done;
    }

    public long getTodo() { return todo; }
    public void setTodo(long todo) { this.todo = todo; }

    public long getInProgress() { return inProgress; }
    public void setInProgress(long inProgress) { this.inProgress = inProgress; }

    public long getDone() { return done; }
    public void setDone(long done) { this.done = done; }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
}
