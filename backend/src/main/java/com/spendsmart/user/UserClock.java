package com.spendsmart.user;

import com.spendsmart.common.FieldValidationException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The single source of "today" and "the current month" for a user, evaluated in the user's own time zone.
 * Using the server's zone instead would, for example, reject a contribution dated today for a user in UTC+5:30
 * between midnight and 05:30 local time, and keep showing last month's budget on the 1st.
 */
@Component
public class UserClock {

    public static final String DEFAULT_ZONE = "UTC";

    /** Region-based IANA zones offered in settings (plus UTC). Legacy aliases and Etc/SystemV zones are omitted. */
    public static final List<String> SUPPORTED_ZONES = ZoneId.getAvailableZoneIds().stream()
            .filter(z -> z.equals("UTC") || (z.contains("/") && !z.startsWith("Etc/") && !z.startsWith("SystemV/")))
            .sorted()
            .toList();

    private static final Set<String> ALLOWED = Set.copyOf(ZoneId.getAvailableZoneIds());

    private final Clock clock;

    public UserClock(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today(User user) {
        return LocalDate.now(clock.withZone(zone(user)));
    }

    public YearMonth currentMonth(User user) {
        return YearMonth.from(today(user));
    }

    public static ZoneId zone(User user) {
        String id = user.getTimeZone();
        return id == null || !ALLOWED.contains(id) ? ZoneId.of(DEFAULT_ZONE) : ZoneId.of(id);
    }

    public static boolean isValidZone(String id) {
        return id != null && ALLOWED.contains(id);
    }

    public static String requireZone(String id, String field) {
        if (!isValidZone(id)) {
            throw new FieldValidationException(field, "Choose a valid time zone.");
        }
        return id;
    }
}
