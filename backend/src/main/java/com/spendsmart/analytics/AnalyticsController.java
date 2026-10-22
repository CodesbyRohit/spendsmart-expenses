package com.spendsmart.analytics;

import com.spendsmart.analytics.AnalyticsDtos.MonthPoint;
import com.spendsmart.analytics.AnalyticsDtos.Overview;
import com.spendsmart.analytics.AnalyticsDtos.RecurringPattern;
import com.spendsmart.common.DateRange;
import com.spendsmart.security.AuthenticatedUser;
import com.spendsmart.user.UserClock;
import com.spendsmart.user.UserLookup;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AnalyticsController {

    private final AnalyticsService analytics;
    private final DashboardService dashboard;
    private final UserLookup userLookup;
    private final UserClock userClock;

    public AnalyticsController(AnalyticsService analytics, DashboardService dashboard, UserLookup userLookup,
                               UserClock userClock) {
        this.analytics = analytics;
        this.dashboard = dashboard;
        this.userLookup = userLookup;
        this.userClock = userClock;
    }

    /** Defaults to the current calendar month and the user's default currency. */
    @GetMapping("/dashboard")
    public DashboardService.Dashboard dashboard(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String currency) {
        return dashboard.build(user.id(), rangeOrCurrentMonth(user, from, to), currency);
    }

    @GetMapping("/analytics/overview")
    public Overview overview(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String currency) {
        return analytics.overview(user.id(), rangeOrCurrentMonth(user, from, to), currencyOrDefault(user, currency));
    }

    @GetMapping("/analytics/monthly")
    public List<MonthPoint> monthly(@AuthenticationPrincipal AuthenticatedUser user,
                                    @RequestParam(required = false) String currency,
                                    @RequestParam(defaultValue = "12") int months) {
        return analytics.monthly(user.id(), currencyOrDefault(user, currency), months);
    }

    @GetMapping("/analytics/recurring")
    public List<RecurringPattern> recurring(@AuthenticationPrincipal AuthenticatedUser user,
                                            @RequestParam(required = false) String currency) {
        return analytics.recurring(user.id(), currencyOrDefault(user, currency), null);
    }

    /** Defaults to the current calendar month in the user's own time zone. */
    private DateRange rangeOrCurrentMonth(AuthenticatedUser user, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return DateRange.of(userClock.currentMonth(userLookup.require(user.id())));
        }
        return new DateRange(from, to);
    }

    private String currencyOrDefault(AuthenticatedUser user, String currency) {
        return currency == null || currency.isBlank() ? userLookup.require(user.id()).getDefaultCurrency() : currency;
    }
}
