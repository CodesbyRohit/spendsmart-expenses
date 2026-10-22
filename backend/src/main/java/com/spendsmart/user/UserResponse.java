package com.spendsmart.user;

import java.time.Instant;
import java.util.UUID;

/** Public view of an account. Never includes the password hash or credential version. */
public record UserResponse(UUID id, String email, String displayName, String defaultCurrency, String locale,
                           String timeZone, Theme theme, boolean budgetAlertsEnabled, int budgetAlertThreshold,
                           Instant createdAt) {

    public static UserResponse of(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getDisplayName(), u.getDefaultCurrency(), u.getLocale(),
                u.getTimeZone(), u.getTheme(), u.isBudgetAlertsEnabled(), u.getBudgetAlertThreshold(), u.getCreatedAt());
    }
}
