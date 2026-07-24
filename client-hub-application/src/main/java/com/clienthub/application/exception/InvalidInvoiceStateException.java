package com.clienthub.application.exception;

/**
 * Controlled domain exception for invoice state changes rejected by business rules.
 */
public class InvalidInvoiceStateException extends RuntimeException {

    public InvalidInvoiceStateException(String message) {
        super(message);
    }
}
