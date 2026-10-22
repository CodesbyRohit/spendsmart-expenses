package com.spendsmart.common;

import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;

/**
 * Published after expenses or budget limits change, so budget alerts can be re-evaluated once the change has
 * committed. Carries only identifiers — no amounts or descriptions.
 */
public record SpendingChangedEvent(UUID userId, Set<Period> affected) {

    public record Period(String currency, YearMonth month) {
    }

    public static SpendingChangedEvent of(UUID userId, String currency, YearMonth month) {
        return new SpendingChangedEvent(userId, Set.of(new Period(currency, month)));
    }
}
