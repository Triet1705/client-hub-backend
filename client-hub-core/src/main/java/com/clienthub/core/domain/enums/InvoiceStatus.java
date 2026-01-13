package com.clienthub.core.domain.enums;

import java.util.Set;

public enum InvoiceStatus {
    DRAFT {
        @Override
        public boolean canTransitionTo(InvoiceStatus next) {
            return Set.of(SENT, CRYPTO_ESCROW_WAITING).contains(next);
        }

        @Override
        public boolean isTerminal() {
            return false;
        }
    },
    SENT {
        @Override
        public boolean canTransitionTo(InvoiceStatus next) {
            return Set.of(PAID, OVERDUE, DRAFT).contains(next);
        }

        @Override
        public boolean isTerminal() {
            return false;
        }
    },
    CRYPTO_ESCROW_WAITING {
        @Override
        public boolean canTransitionTo(InvoiceStatus next) {
            return Set.of(DEPOSIT_DETECTED, DRAFT, EXPIRED).contains(next);
        }

        @Override
        public boolean isTerminal() {
            return false;
        }
    },
    DEPOSIT_DETECTED {
        @Override
        public boolean canTransitionTo(InvoiceStatus next) {
            return Set.of(LOCKED, DRAFT).contains(next);
        }

        @Override
        public boolean isTerminal() {
            return false;
        }
    },
    LOCKED {
        @Override
        public boolean canTransitionTo(InvoiceStatus next) {
            return Set.of(PAID, REFUNDED).contains(next);
        }

        @Override
        public boolean isTerminal() {
            return false;
        }
    },
    DISPUTED {
        @Override
        public boolean canTransitionTo(InvoiceStatus next) {
            return Set.of(PAID, REFUNDED, LOCKED).contains(next);
        }

        @Override
        public boolean isTerminal() {
            return false;
        }
    },
    PAID {
        @Override
        public boolean canTransitionTo(InvoiceStatus next) {
            return false;
        }

        @Override
        public boolean isTerminal() {
            return false;
        }
    },
    REFUNDED {
        @Override
        public boolean canTransitionTo(InvoiceStatus next) {
            return false;
        }

        @Override
        public boolean isTerminal() {
            return false;
        }
    },
    OVERDUE {
        @Override
        public boolean canTransitionTo(InvoiceStatus next) {
            return Set.of(PAID, DRAFT).contains(next);
        }

        @Override
        public boolean isTerminal() {
            return false;
        }
    },

    EXPIRED {
        @Override
        public boolean canTransitionTo(InvoiceStatus next) {
            return next == DRAFT;
        }

        @Override
        public boolean isTerminal() {
            return false;
        }
    };

    public abstract boolean canTransitionTo(InvoiceStatus nextStatus);

    public abstract boolean isTerminal();
}
