package com.spendsmart.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendsmart.ApiTestSupport;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyticsApiTest extends ApiTestSupport {

    @Test
    void overviewTotalsMatchRecordedTransactionsWithInclusiveBoundaries() throws Exception {
        Client c = register();
        createTx(c, "INCOME", "60000", c.income("Salary"), "2026-10-01", "Salary");
        createTx(c, "EXPENSE", "15000", c.expense("Housing"), "2026-10-01", "Rent (first day)");
        createTx(c, "EXPENSE", "2000.40", c.expense("Groceries"), "2026-10-31", "Groceries (last day)");
        createTx(c, "EXPENSE", "0.60", c.expense("Groceries"), "2026-10-15", "Change");
        createTx(c, "EXPENSE", "777", c.expense("Groceries"), "2026-11-01", "Outside range");
        createTx(c, "EXPENSE", "12", "USD", c.expense("Groceries"), "2026-10-10", "Other currency", null);
        createTx(c, "EXPENSE", "10000", c.expense("Housing"), "2026-09-01", "September rent");

        JsonNode o = read(getAs(c, "/api/analytics/overview?from=2026-10-01&to=2026-10-31&currency=INR")
                .andExpect(status().isOk()));
        assertThat(o.at("/totals/income").asText()).isEqualTo("60000.00");
        assertThat(o.at("/totals/expenses").asText()).isEqualTo("17001.00");
        assertThat(o.at("/totals/net").asText()).isEqualTo("42999.00");
        assertThat(o.at("/totals/expenseCount").asLong()).isEqualTo(3);
        assertThat(o.at("/previousRange/from").asText()).isEqualTo("2026-09-01");
        assertThat(o.at("/previousTotals/expenses").asText()).isEqualTo("10000.00");
        assertThat(o.at("/comparison/expenseChangePercent").asText()).isEqualTo("70.0");
        // No income in September: the change is undefined, so it is omitted rather than invented.
        assertThat(o.at("/comparison/incomeChangePercent").isMissingNode()).isTrue();
        assertThat(o.at("/savingsRatePercent").asText()).isEqualTo("71.7");
        assertThat(o.at("/averageDailyExpense").asText()).isEqualTo("548.42");

        assertThat(o.at("/expenseCategories/0/name").asText()).isEqualTo("Housing");
        assertThat(o.at("/expenseCategories/0/sharePercent").asText()).isEqualTo("88.2");
        assertThat(o.at("/expenseCategories/1/total").asText()).isEqualTo("2001.00");

        assertThat(o.at("/granularity").asText()).isEqualTo("DAY");
        assertThat(o.get("trend")).hasSize(31);
        assertThat(o.at("/trend/0/expenses").asText()).isEqualTo("15000.00");
        assertThat(o.at("/trend/30/expenses").asText()).isEqualTo("2000.40");
        assertThat(o.at("/trend/1/expenses").asText()).isEqualTo("0.00");

        List<String> rules = new ArrayList<>();
        o.get("insights").forEach(i -> rules.add(i.get("rule").asText()));
        assertThat(rules).contains("expense-change", "savings-rate", "largest-expense")
                .doesNotContain("expense-change-no-baseline");
        // The largest expense comes from a top-1 query; it must be the in-range, in-currency maximum.
        o.get("insights").forEach(i -> {
            if (i.get("rule").asText().equals("largest-expense")) {
                assertThat(i.get("message").asText()).contains("Rent (first day)").contains("88.2%");
            }
        });
    }

    @Test
    void zeroBaselineProducesAnExplanationNotAPercentage() throws Exception {
        Client c = register();
        createTx(c, "EXPENSE", "500", c.expense("Groceries"), "2026-10-05", "First ever expense");
        JsonNode o = read(getAs(c, "/api/analytics/overview?from=2026-10-01&to=2026-10-31&currency=INR"));
        assertThat(o.at("/comparison/expenseChangePercent").isMissingNode()).isTrue();
        assertThat(o.get("insights").toString())
                .contains("expense-change-no-baseline")
                .contains("no-income")
                .doesNotContain("Infinity").doesNotContain("NaN");
    }

    @Test
    void emptyAccountHasZeroTotalsAndNoInsights() throws Exception {
        Client c = register();
        JsonNode d = read(getAs(c, "/api/dashboard").andExpect(status().isOk()));
        assertThat(d.get("hasAnyTransactions").asBoolean()).isFalse();
        assertThat(d.get("sampleDataPresent").asBoolean()).isFalse();
        assertThat(d.at("/overview/totals/expenses").asText()).isEqualTo("0.00");
        assertThat(d.at("/overview/range/from").asText()).isEqualTo("2026-10-01");
        assertThat(d.at("/overview/insights")).isEmpty();
        assertThat(d.at("/budget/planExists").asBoolean()).isFalse();
        assertThat(d.get("recentTransactions")).isEmpty();
    }

    @Test
    void monthlySeriesProvidesMonthOverMonthChanges() throws Exception {
        Client c = register();
        createTx(c, "EXPENSE", "1000", c.expense("Groceries"), "2026-08-10", "Aug");
        createTx(c, "EXPENSE", "1500", c.expense("Groceries"), "2026-09-10", "Sep");
        createTx(c, "INCOME", "5000", c.income("Salary"), "2026-10-01", "Oct pay");
        JsonNode m = read(getAs(c, "/api/analytics/monthly?currency=INR&months=3"));
        assertThat(m).hasSize(3);
        assertThat(m.at("/0/month").asText()).isEqualTo("2026-08");
        assertThat(m.at("/0/expenseChangePercent").isMissingNode()).isTrue(); // July had nothing
        assertThat(m.at("/1/expenseChangePercent").asText()).isEqualTo("50.0");
        assertThat(m.at("/2/expenseChangePercent").asText()).isEqualTo("-100.0");
        assertThat(m.at("/2/net").asText()).isEqualTo("5000.00");
    }

    @Test
    void longRangesAreBucketedWeeklyOrMonthly() throws Exception {
        Client c = register();
        createTx(c, "EXPENSE", "100", c.expense("Groceries"), "2026-03-02", "Mar");
        JsonNode weekly = read(getAs(c, "/api/analytics/overview?from=2026-01-01&to=2026-06-30&currency=INR"));
        assertThat(weekly.get("granularity").asText()).isEqualTo("WEEK");
        assertThat(weekly.get("trend")).hasSize(26);
        JsonNode monthly = read(getAs(c, "/api/analytics/overview?from=2025-01-01&to=2026-10-31&currency=INR"));
        assertThat(monthly.get("granularity").asText()).isEqualTo("MONTH");
        assertThat(monthly.get("trend")).hasSize(22);
    }

    @Test
    void recurringEndpointOnlyReportsRegularPatterns() throws Exception {
        Client c = register();
        for (String d : List.of("2026-07-12", "2026-08-12", "2026-09-12", "2026-10-12")) {
            createTx(c, "EXPENSE", "649", "INR", c.expense("Subscriptions"), d, "Video plan", "StreamBox");
        }
        createTx(c, "EXPENSE", "300", "INR", c.expense("Food and Dining"), "2026-09-03", "Dinner", "Cafe");
        createTx(c, "EXPENSE", "300", "INR", c.expense("Food and Dining"), "2026-10-09", "Dinner", "Cafe");
        JsonNode r = read(getAs(c, "/api/analytics/recurring?currency=INR"));
        assertThat(r).hasSize(1);
        assertThat(r.at("/0/label").asText()).isEqualTo("StreamBox");
        assertThat(r.at("/0/cadence").asText()).isEqualTo("MONTHLY");
    }
}
