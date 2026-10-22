package com.spendsmart.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record Range(LocalDate from, LocalDate to, long days) {
    }

    public record Totals(BigDecimal income, BigDecimal expenses, BigDecimal net, long incomeCount, long expenseCount) {
    }

    /** Percentages are null when the previous value is zero — the UI explains why instead of showing a number. */
    public record Comparison(BigDecimal incomeChangePercent, BigDecimal expenseChangePercent, BigDecimal netDelta) {
    }

    public record CategorySlice(UUID categoryId, String name, String icon, BigDecimal total, long count,
                                BigDecimal sharePercent, BigDecimal previousTotal, BigDecimal changePercent) {
    }

    public enum Granularity { DAY, WEEK, MONTH }

    public record TrendPoint(LocalDate start, LocalDate end, BigDecimal income, BigDecimal expenses) {
    }

    public enum Tone { POSITIVE, NEUTRAL, WARNING }

    /** {@code rule} identifies the deterministic rule that produced the sentence, for transparency and testing. */
    public record Insight(String rule, Tone tone, String message) {
    }

    public record Overview(
            String currency,
            Range range,
            Range previousRange,
            Totals totals,
            Totals previousTotals,
            Comparison comparison,
            /* (income − expenses) ÷ income × 100; null without income. Not a bank balance. */
            BigDecimal savingsRatePercent,
            BigDecimal averageDailyExpense,
            List<CategorySlice> expenseCategories,
            List<CategorySlice> incomeCategories,
            Granularity granularity,
            List<TrendPoint> trend,
            List<Insight> insights) {
    }

    public record MonthPoint(YearMonth month, BigDecimal income, BigDecimal expenses, BigDecimal net,
                             BigDecimal expenseChangePercent) {
    }

    public enum Cadence { WEEKLY, BIWEEKLY, MONTHLY }

    public record RecurringPattern(String label, String categoryName, Cadence cadence, BigDecimal typicalAmount,
                                   int occurrences, LocalDate firstDate, LocalDate lastDate,
                                   LocalDate nextExpectedDate, BigDecimal monthlyEquivalent) {
    }
}
