package com.clienthub.application.exception;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

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
        super("This invoice is higher than the project's available budget. "
                + "You can invoice up to "
                + formatCurrency(remaining)
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

    private static String formatCurrency(BigDecimal amount) {
        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.US);
        currency.setMinimumFractionDigits(2);
        currency.setMaximumFractionDigits(2);
        return currency.format(amount);
    }
}
