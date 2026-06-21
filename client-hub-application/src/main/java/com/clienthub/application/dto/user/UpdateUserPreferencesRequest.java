package com.clienthub.application.dto.user;

import jakarta.validation.constraints.Pattern;

public record UpdateUserPreferencesRequest(
        @Pattern(regexp = "^(dark|light)$", message = "Theme must be dark or light")
        String theme,

        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
        String currency,

        @Pattern(regexp = "^(DD/MM/YYYY|MM/DD/YYYY|YYYY-MM-DD)$", message = "Unsupported date format")
        String dateFormat,

        String timezone,
        Boolean notifyComments,
        Boolean notifyTasks,
        Boolean notifyProjects,
        Boolean notifyInvoices,
        Boolean quietHoursEnabled,

        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Quiet hours start must be HH:mm")
        String quietHoursStart,

        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Quiet hours end must be HH:mm")
        String quietHoursEnd
) {}
