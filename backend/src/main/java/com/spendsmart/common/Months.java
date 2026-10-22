package com.spendsmart.common;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

public final class Months {

    private static final YearMonth MIN = YearMonth.of(1970, 1);
    private static final YearMonth MAX = YearMonth.of(2100, 12);

    private Months() {
    }

    /** Parses an ISO {@code yyyy-MM} month from a path or query parameter. */
    public static YearMonth parse(String value, String field) {
        try {
            YearMonth month = YearMonth.parse(value);
            if (month.isBefore(MIN) || month.isAfter(MAX)) {
                throw new FieldValidationException(field, "Choose a month between 1970 and 2100.");
            }
            return month;
        } catch (DateTimeParseException | NullPointerException e) {
            throw new FieldValidationException(field, "Use the format YYYY-MM.");
        }
    }
}
