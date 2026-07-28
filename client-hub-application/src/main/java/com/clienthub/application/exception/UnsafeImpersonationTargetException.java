package com.clienthub.application.exception;

/**
 * Raised when an Administrator attempts to impersonate a disabled or locked account.
 */
public class UnsafeImpersonationTargetException extends RuntimeException {

    public UnsafeImpersonationTargetException() {
        super("Inactive or locked accounts cannot be impersonated.");
    }
}
