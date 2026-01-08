package com.clienthub.core.domain.enums;

public enum Role {
    ROLE_ADMIN,
    ROLE_FREELANCER,
    ROLE_CLIENT;

    public String getDisplayValue() {
        return this.name().replace("ROLE_", "");
    }
}
