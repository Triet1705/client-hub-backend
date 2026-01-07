package com.clienthub.api.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * Response DTO for successful user registration
 */
public class RegisterResponse {

    private String message;

    @JsonProperty("user_id")
    private UUID userId;

    private String email;

    @JsonProperty("full_name")
    private String fullName;

    public RegisterResponse() {
    }

    public RegisterResponse(String message, UUID userId, String email, String fullName) {
        this.message = message;
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
}
