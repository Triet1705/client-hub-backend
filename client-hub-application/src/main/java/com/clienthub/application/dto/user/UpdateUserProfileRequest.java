package com.clienthub.application.dto.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateUserProfileRequest(
        @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
        String fullName,

        @Size(max = 160, message = "Headline must be 160 characters or fewer")
        String headline,

        @Size(max = 1000, message = "Bio must be 1000 characters or fewer")
        String bio,

        @Size(max = 20, message = "At most 20 skills are allowed")
        List<@Size(max = 40, message = "Each skill must be 40 characters or fewer") String> skills,

        @Size(max = 500, message = "Portfolio URL must be 500 characters or fewer")
        @Pattern(regexp = "^(https?://.+)?$", message = "Portfolio URL must start with http:// or https://")
        String portfolioUrl,

        Boolean publicProfile,
        Boolean showEmail,
        Boolean showWallet
) {}
