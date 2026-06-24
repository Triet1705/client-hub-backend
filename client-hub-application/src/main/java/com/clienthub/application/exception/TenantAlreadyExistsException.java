package com.clienthub.application.exception;

public class TenantAlreadyExistsException extends RuntimeException {

    public TenantAlreadyExistsException(String tenantId) {
        super("Workspace already exists: " + tenantId);
    }
}
