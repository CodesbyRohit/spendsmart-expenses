package com.spendsmart.common;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/** Inclusive calendar date range. Both ends are included in every query that uses it. */
public record DateRange(LocalDate from, LocalDate to) {

    /** Analytics requests are capped to keep aggregate queries and chart payloads bounded. */
    public static final long MAX_DAYS = 366L * 5;

    public DateRange {
        if (from == null || to == null) {
            throw new FieldValidationException("from", "Choose a start and end date.");
        }
        if (to.isBefore(from)) {
            throw new FieldValidationException("to", "End date must be on or after the start date.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new FieldValidationException("from", "Date range can be at most five years.");
        }
    }

    public static DateRange of(YearMonth month) {
        return new DateRange(month.atDay(1), month.atEndOfMonth());
    }

    public long days() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    /**
     * The comparison period. A range covering whole calendar months compares against the same number of preceding
     * whole months (so October is compared with September, not with the 31 days before it); any other range compares
     * against the same number of days immediately before it.
     */
    public DateRange previous() {
        if (from.getDayOfMonth() == 1 && to.equals(YearMonth.from(to).atEndOfMonth())) {
            long months = ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to)) + 1;
            YearMonth start = YearMonth.from(from).minusMonths(months);
            return new DateRange(start.atDay(1), YearMonth.from(from).minusMonths(1).atEndOfMonth());
        }
        return new DateRange(from.minusDays(days()), from.minusDays(1));
    }

    public boolean isSingleCalendarMonth() {
        return from.getDayOfMonth() == 1 && YearMonth.from(from).equals(YearMonth.from(to))
                && to.equals(YearMonth.from(to).atEndOfMonth());
    }
}
