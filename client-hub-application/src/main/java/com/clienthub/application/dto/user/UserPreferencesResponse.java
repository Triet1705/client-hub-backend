package com.clienthub.application.dto.user;

import com.clienthub.domain.entity.UserPreferences;

public record UserPreferencesResponse(
        String theme,
        String currency,
        String dateFormat,
        String timezone,
        boolean notifyComments,
        boolean notifyTasks,
        boolean notifyProjects,
        boolean notifyInvoices,
        boolean quietHoursEnabled,
        String quietHoursStart,
        String quietHoursEnd
) {
    public static UserPreferencesResponse from(UserPreferences preferences) {
        return new UserPreferencesResponse(
                preferences.getTheme(),
                preferences.getCurrency(),
                preferences.getDateFormat(),
                preferences.getTimezone(),
                preferences.isNotifyComments(),
                preferences.isNotifyTasks(),
                preferences.isNotifyProjects(),
                preferences.isNotifyInvoices(),
                preferences.isQuietHoursEnabled(),
                preferences.getQuietHoursStart(),
                preferences.getQuietHoursEnd()
        );
    }
}
