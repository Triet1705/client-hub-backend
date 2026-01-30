package com.clienthub.domain.enums;

public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,

    LOGIN,
    LOGIN_FAILED,
    LOGOUT,

    USER_LOCKED,
    USER_UNLOCKED,
    ADMIN_IMPERSONATION,

    INVOICE_SENT,
    INVOICE_PAID,
    INVOICE_CANCELLED,

    DISPUTE_OPENED,
    DISPUTE_RESOLVED,

    ANCHOR_SUCCESS,
    ANCHOR_FAILED
}