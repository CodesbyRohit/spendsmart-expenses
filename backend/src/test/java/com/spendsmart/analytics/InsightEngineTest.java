package com.spendsmart.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendsmart.analytics.AnalyticsDtos.CategorySlice;
import com.spendsmart.analytics.AnalyticsDtos.Insight;
import com.spendsmart.analytics.AnalyticsDtos.Totals;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InsightEngineTest {

    private static Totals totals(String income, String expenses, long incomeCount, long expenseCount) {
        BigDecimal in = new BigDecimal(income);
        BigDecimal out = new BigDecimal(expenses);
        return new Totals(in, out, in.subtract(out), incomeCount, expenseCount);
    }

    private static CategorySlice slice(String name, String total, String share, String previous, String change) {
        return new CategorySlice(UUID.randomUUID(), name, "circle-dot", new BigDecimal(total), 3,
                share == null ? null : new BigDecimal(share), new BigDecimal(previous),
                change == null ? null : new BigDecimal(change));
    }

    private static List<Insight> run(Totals current, Totals previous, List<CategorySlice> slices) {
        return InsightEngine.generate(new InsightEngine.Input("INR", Locale.forLanguageTag("en-IN"), true, current,
                previous, slices, null, null, List.of()));
    }

    @Test
    void reportsCategoryIncreaseWithExactPercentage() {
        List<Insight> out = run(totals("0", "1180", 0, 4), totals("0", "1000", 0, 4),
                List.of(slice("Food and Dining", "1180", "100", "1000", "18.0")));
        assertThat(out).extracting(Insight::message)
                .anyMatch(m -> m.startsWith("Your Food and Dining spending increased 18% compared with the previous month"));
    }

    @Test
    void ignoresSmallMovementsAndTinyCategories() {
        List<Insight> out = run(totals("0", "10000", 0, 9), totals("0", "9900", 0, 9), List.of(
                slice("Rent", "9000", "90", "8950", "0.6"),
                slice("Snacks", "100", "1", "20", "400")));
        assertThat(out).extracting(Insight::rule).doesNotContain("category-change");
    }

    @Test
    void topThreeShareRequiresMoreThanThreeCategories() {
        List<CategorySlice> three = List.of(slice("A", "50", "50", "0", null), slice("B", "30", "30", "0", null),
                slice("C", "20", "20", "0", null));
        assertThat(run(totals("0", "100", 0, 3), totals("0", "0", 0, 0), three))
                .extracting(Insight::rule).doesNotContain("top-three-share");

        List<CategorySlice> four = List.of(slice("A", "40", "40", "0", null), slice("B", "17", "17", "0", null),
                slice("C", "10", "10", "0", null), slice("D", "33", "33", "0", null));
        // Slices arrive sorted by total in production; the rule takes the first three.
        assertThat(run(totals("0", "100", 0, 4), totals("0", "0", 0, 0), four))
                .filteredOn(i -> i.rule().equals("top-three-share"))
                .singleElement()
                .satisfies(i -> assertThat(i.message()).contains("(A, B and C) account for 67%"));
    }

    @Test
    void overspendingIncomeIsAWarningAndSavingsRateIsReportedOtherwise() {
        assertThat(run(totals("1000", "1500", 1, 2), totals("0", "0", 0, 0), List.of()))
                .filteredOn(i -> i.rule().equals("overspent-income")).singleElement()
                .satisfies(i -> assertThat(i.message()).contains("₹500.00"));
        assertThat(run(totals("1000", "750", 1, 2), totals("0", "0", 0, 0), List.of()))
                .filteredOn(i -> i.rule().equals("savings-rate")).singleElement()
                .satisfies(i -> assertThat(i.message()).contains("25%"));
    }

    @Test
    void noDataMeansNoInsights() {
        assertThat(run(totals("0", "0", 0, 0), totals("0", "500", 0, 2), List.of())).isEmpty();
    }
}
