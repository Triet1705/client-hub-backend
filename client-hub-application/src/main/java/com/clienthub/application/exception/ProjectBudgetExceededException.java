package com.clienthub.application.exception;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Raised when a new invoice would over-commit a project's FIAT budget.
 */
public class ProjectBudgetExceededException extends RuntimeException {

    private final String budget;
    private final String committed;
    private final String requested;
    private final String remaining;

    public ProjectBudgetExceededException(
            BigDecimal budget,
            BigDecimal committed,
            BigDecimal requested,
            BigDecimal remaining) {
        super("Project budget exceeded. Remaining budget is $"
                + format(remaining)
                + ", but this invoice requests $"
                + format(requested)
                + ".");
        this.budget = format(budget);
        this.committed = format(committed);
        this.requested = format(requested);
        this.remaining = format(remaining);
    }

    public String getBudget() {
        return budget;
    }

    public String getCommitted() {
        return committed;
    }

    public String getRequested() {
        return requested;
    }

    public String getRemaining() {
        return remaining;
    }

    private static String format(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
