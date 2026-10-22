package com.spendsmart.budget;

import com.spendsmart.common.Money;
import com.spendsmart.common.Percent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Pure budget arithmetic — no I/O, so every rule is unit-testable.
 *
 * <h2>Definitions</h2>
 * <ul>
 *   <li><b>Spent (category line)</b> = Σ EXPENSE transactions in that category, currency and calendar month.</li>
 *   <li><b>Spent (total)</b> = Σ of <i>all</i> EXPENSE transactions in the currency and month, whether or not their
 *       category has a line. Category lines are a breakdown of the total, never added to it, so nothing is
 *       double-counted.</li>
 *   <li><b>Utilisation %</b> = spent ÷ limit × 100, one decimal, HALF_UP. Not capped — 132.5 % is reported as such.</li>
 *   <li><b>Bar %</b> = utilisation clamped to 0–100 for progress-bar widths.</li>
 *   <li><b>Remaining</b> = limit − spent (negative when overspent); <b>overBy</b> = max(0, spent − limit).</li>
 *   <li><b>Status</b>: EXCEEDED if spent &gt; limit; WARNING if spent × 100 ≥ limit × alert threshold (exact,
 *       unrounded); otherwise ON_TRACK.
 *       NO_LIMIT when there is no limit to measure against.</li>
 * </ul>
 */
public final class BudgetCalculator {

    private BudgetCalculator() {
    }

    public enum Status { NO_LIMIT, ON_TRACK, WARNING, EXCEEDED }

    public record LineInput(UUID categoryId, BigDecimal limit) {
    }

    public record Measure(BigDecimal limit, BigDecimal spent, BigDecimal remaining, BigDecimal overBy,
                          BigDecimal percentUsed, BigDecimal barPercent, Status status) {
    }

    public record LineResult(UUID categoryId, Measure measure) {
    }

    public record Result(Measure total, List<LineResult> lines, BigDecimal allocated, BigDecimal unbudgetedSpent,
                         boolean overAllocated) {
    }

    public static Result calculate(BigDecimal totalLimit, List<LineInput> lines, Map<UUID, BigDecimal> spentByCategory,
                                   int thresholdPercent, String currency) {
        BigDecimal totalSpent = spentByCategory.values().stream().map(Money::nz).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<LineResult> lineResults = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (LineInput line : lines) {
            BigDecimal spent = Money.nz(spentByCategory.get(line.categoryId()));
            lineResults.add(new LineResult(line.categoryId(), measure(line.limit(), spent, thresholdPercent, currency)));
            allocated = allocated.add(line.limit());
        }
        Set<UUID> budgeted = lines.stream().map(LineInput::categoryId).collect(Collectors.toSet());
        BigDecimal unbudgeted = spentByCategory.entrySet().stream()
                .filter(e -> !budgeted.contains(e.getKey()))
                .map(e -> Money.nz(e.getValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean overAllocated = totalLimit != null && allocated.compareTo(totalLimit) > 0;
        return new Result(measure(totalLimit, totalSpent, thresholdPercent, currency), lineResults,
                Money.scale(allocated, currency), Money.scale(unbudgeted, currency), overAllocated);
    }

    public static Measure measure(BigDecimal limit, BigDecimal spent, int thresholdPercent, String currency) {
        BigDecimal s = Money.scale(spent, currency);
        if (limit == null || limit.signum() <= 0) {
            return new Measure(null, s, null, null, null, null, Status.NO_LIMIT);
        }
        BigDecimal l = Money.scale(limit, currency);
        BigDecimal remaining = l.subtract(s);
        BigDecimal overBy = remaining.signum() < 0 ? remaining.negate() : Money.scale(BigDecimal.ZERO, currency);
        BigDecimal percent = Percent.of(s, l);
        Status status;
        if (s.compareTo(l) > 0) {
            status = Status.EXCEEDED;
        } else if (s.multiply(BigDecimal.valueOf(100)).compareTo(l.multiply(BigDecimal.valueOf(thresholdPercent))) >= 0) {
            // Compared exactly (spent × 100 ≥ limit × threshold), not via the rounded display percentage,
            // so 79.99 of 100 is not flagged at an 80 % threshold.
            status = Status.WARNING;
        } else {
            status = Status.ON_TRACK;
        }
        return new Measure(l, s, remaining, overBy, percent, Percent.clampForBar(percent), status);
    }

    /** Remaining budget spread over the days left in the month including today; null when nothing remains. */
    public static BigDecimal dailyAllowance(BigDecimal remaining, long daysLeftIncludingToday, String currency) {
        if (remaining == null || remaining.signum() <= 0 || daysLeftIncludingToday <= 0) {
            return null;
        }
        return remaining.divide(BigDecimal.valueOf(daysLeftIncludingToday), Money.minorUnits(currency), RoundingMode.DOWN);
    }
}
