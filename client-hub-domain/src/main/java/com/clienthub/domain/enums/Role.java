package com.clienthub.domain.enums;

public enum Role {
    ADMIN,
    FREELANCER,
    CLIENT;

    public String getDisplayValue() {
        return this.name().replace("ROLE_", "");
    }
}
