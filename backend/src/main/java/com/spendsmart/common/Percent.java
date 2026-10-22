package com.spendsmart.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Percentage helpers that refuse to invent a value when the baseline is zero. */
public final class Percent {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Percent() {
    }

    /** {@code part / whole * 100} with one decimal place, or {@code null} when {@code whole} is zero. */
    public static BigDecimal of(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) {
            return null;
        }
        return Money.nz(part).multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP);
    }

    /** Relative change from {@code previous} to {@code current}, or {@code null} when the previous value is zero. */
    public static BigDecimal change(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) {
            return null;
        }
        return Money.nz(current).subtract(previous).multiply(HUNDRED).divide(previous.abs(), 1, RoundingMode.HALF_UP);
    }

    /** Value suitable for a progress-bar width: clamped to 0–100 so overspending never produces a broken bar. */
    public static BigDecimal clampForBar(BigDecimal percent) {
        if (percent == null || percent.signum() < 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return percent.min(HUNDRED.setScale(1));
    }
}
