package com.spendsmart.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendsmart.ApiTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BudgetApiTest extends ApiTestSupport {

    @Test
    void budgetReflectsPersistedExpensesWithoutDoubleCounting() throws Exception {
        Client c = register();
        putAs(c, "/api/budgets/2026-10", Map.of("currency", "INR", "totalLimit", "10000", "lines", List.of(
                Map.of("categoryId", c.expense("Groceries"), "limit", "3000"),
                Map.of("categoryId", c.expense("Transportation"), "limit", "1000")))).andExpect(status().isOk());

        createTx(c, "EXPENSE", "2500", c.expense("Groceries"), "2026-10-03", "Groceries");
        createTx(c, "EXPENSE", "1200", c.expense("Transportation"), "2026-10-04", "Cabs");
        createTx(c, "EXPENSE", "800", c.expense("Entertainment"), "2026-10-31", "Concert (last day counts)");
        // None of these may count toward October INR spending:
        createTx(c, "INCOME", "50000", c.income("Salary"), "2026-10-01", "Salary");
        createTx(c, "EXPENSE", "999", c.expense("Groceries"), "2026-09-30", "Previous month");
        createTx(c, "EXPENSE", "999", c.expense("Groceries"), "2026-11-01", "Next month");
        createTx(c, "EXPENSE", "99", "USD", c.expense("Groceries"), "2026-10-05", "Other currency", null);

        JsonNode b = read(getAs(c, "/api/budgets/2026-10?currency=INR").andExpect(status().isOk()));
        // Total = every October INR expense once: 2500 + 1200 + 800.
        assertThat(b.at("/total/spent").asText()).isEqualTo("4500.00");
        assertThat(b.at("/total/remaining").asText()).isEqualTo("5500.00");
        assertThat(b.at("/total/percentUsed").asText()).isEqualTo("45.0");
        assertThat(b.at("/total/status").asText()).isEqualTo("ON_TRACK");
        assertThat(b.at("/allocated").asText()).isEqualTo("4000.00");
        assertThat(b.at("/unbudgetedSpent").asText()).isEqualTo("800.00");

        JsonNode transport = line(b, "Transportation");
        assertThat(transport.at("/measure/spent").asText()).isEqualTo("1200.00");
        assertThat(transport.at("/measure/percentUsed").asText()).isEqualTo("120.0");
        assertThat(transport.at("/measure/barPercent").asText()).isEqualTo("100.0");
        assertThat(transport.at("/measure/remaining").asText()).isEqualTo("-200.00");
        assertThat(transport.at("/measure/overBy").asText()).isEqualTo("200.00");
        assertThat(transport.at("/measure/status").asText()).isEqualTo("EXCEEDED");

        JsonNode groceries = line(b, "Groceries");
        assertThat(groceries.at("/measure/percentUsed").asText()).isEqualTo("83.3");
        assertThat(groceries.at("/measure/status").asText()).isEqualTo("WARNING");
        // Most at-risk line first.
        assertThat(b.at("/lines/0/category/name").asText()).isEqualTo("Transportation");
        // Current month: days left includes today (15th..31st = 17 days); 5500 / 17 rounded down.
        assertThat(b.at("/daysLeft").asLong()).isEqualTo(17);
        assertThat(b.at("/dailyAllowance").asText()).isEqualTo("323.52");
    }

    @Test
    void editingAndDeletingTransactionsUpdatesBudgetImmediately() throws Exception {
        Client c = register();
        putAs(c, "/api/budgets/2026-10", Map.of("currency", "INR", "lines",
                List.of(Map.of("categoryId", c.expense("Groceries"), "limit", "1000")))).andExpect(status().isOk());
        JsonNode tx = createTx(c, "EXPENSE", "400", c.expense("Groceries"), TODAY, "Shop");
        assertThat(spent(c, "Groceries")).isEqualTo("400.00");

        Map<String, Object> moved = tx("EXPENSE", "400", c.expense("Shopping"), TODAY, "Shop");
        moved.put("version", tx.get("version").asLong());
        JsonNode updated = read(putAs(c, "/api/transactions/" + tx.get("id").asText(), moved).andExpect(status().isOk()));
        assertThat(spent(c, "Groceries")).isEqualTo("0.00");

        Map<String, Object> back = tx("EXPENSE", "650", c.expense("Groceries"), TODAY, "Shop");
        back.put("version", updated.get("version").asLong());
        putAs(c, "/api/transactions/" + tx.get("id").asText(), back).andExpect(status().isOk());
        assertThat(spent(c, "Groceries")).isEqualTo("650.00");

        deleteAs(c, "/api/transactions/" + tx.get("id").asText()).andExpect(status().isNoContent());
        assertThat(spent(c, "Groceries")).isEqualTo("0.00");
    }

    @Test
    void thresholdAndExceededAlertsAreCreatedOnceEach() throws Exception {
        Client c = register();
        putAs(c, "/api/budgets/2026-10", Map.of("currency", "INR", "lines",
                List.of(Map.of("categoryId", c.expense("Food and Dining"), "limit", "1000")))).andExpect(status().isOk());

        createTx(c, "EXPENSE", "500", c.expense("Food and Dining"), TODAY, "Dinner");   // 50% — no alert
        assertThat(read(getAs(c, "/api/notifications")).get("items")).isEmpty();
        createTx(c, "EXPENSE", "350", c.expense("Food and Dining"), TODAY, "Lunch");    // 85% — threshold alert
        createTx(c, "EXPENSE", "50", c.expense("Food and Dining"), TODAY, "Coffee");    // 90% — no duplicate
        createTx(c, "EXPENSE", "200", c.expense("Food and Dining"), TODAY, "Party");    // 110% — exceeded alert
        createTx(c, "EXPENSE", "10", c.expense("Food and Dining"), TODAY, "Snack");     // still exceeded — no duplicate

        JsonNode list = read(getAs(c, "/api/notifications"));
        assertThat(list.get("items")).hasSize(2);
        assertThat(list.get("unreadCount").asLong()).isEqualTo(2);
        assertThat(list.at("/items/0/type").asText()).isEqualTo("BUDGET_EXCEEDED");
        assertThat(list.at("/items/1/type").asText()).isEqualTo("BUDGET_THRESHOLD");
        assertThat(list.at("/items/1/message").asText()).contains("85% of your food and dining budget");

        postAs(c, "/api/notifications/read-all", null).andExpect(status().isNoContent());
        getAs(c, "/api/notifications").andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void alertsRespectPreferencesAndPastMonthsDoNotAlert() throws Exception {
        Client c = register();
        putAs(c, "/api/budgets/2026-09", Map.of("currency", "INR", "totalLimit", "100", "lines", List.of()))
                .andExpect(status().isOk());
        createTx(c, "EXPENSE", "500", c.expense("Groceries"), "2026-09-10", "Back-dated");
        assertThat(read(getAs(c, "/api/notifications")).get("items")).isEmpty();

        putAs(c, "/api/account/preferences", Map.of("defaultCurrency", "INR", "locale", "en-IN", "theme", "SYSTEM",
                "budgetAlertsEnabled", false, "budgetAlertThreshold", 80)).andExpect(status().isOk());
        putAs(c, "/api/budgets/2026-10", Map.of("currency", "INR", "totalLimit", "100", "lines", List.of()))
                .andExpect(status().isOk());
        createTx(c, "EXPENSE", "500", c.expense("Groceries"), TODAY, "Over");
        assertThat(read(getAs(c, "/api/notifications")).get("items")).isEmpty();
    }

    @Test
    void validationRulesForBudgets() throws Exception {
        Client c = register();
        putAs(c, "/api/budgets/2026-10", Map.of("currency", "INR", "lines", List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("totalLimit"));
        putAs(c, "/api/budgets/2026-10", Map.of("currency", "INR", "lines",
                List.of(Map.of("categoryId", c.income("Salary"), "limit", "100"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Budgets can only use expense categories."));
        putAs(c, "/api/budgets/2026-10", Map.of("currency", "INR", "lines", List.of(
                Map.of("categoryId", c.expense("Groceries"), "limit", "100"),
                Map.of("categoryId", c.expense("Groceries"), "limit", "200"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("lines[1].categoryId"));
        putAs(c, "/api/budgets/2026-10", Map.of("currency", "INR", "totalLimit", "-1", "lines", List.of()))
                .andExpect(status().isBadRequest());
        getAs(c, "/api/budgets/2026-13?currency=INR").andExpect(status().isBadRequest());
    }

    @Test
    void editingKeepsLinesAndHistoryAndCopyWork() throws Exception {
        Client c = register();
        putAs(c, "/api/budgets/2026-09", Map.of("currency", "INR", "totalLimit", "5000", "lines", List.of(
                Map.of("categoryId", c.expense("Groceries"), "limit", "2000")))).andExpect(status().isOk());
        // Re-saving with the same category must update in place (no unique-constraint clash).
        putAs(c, "/api/budgets/2026-09", Map.of("currency", "INR", "totalLimit", "6000", "lines", List.of(
                Map.of("categoryId", c.expense("Groceries"), "limit", "2500"),
                Map.of("categoryId", c.expense("Travel"), "limit", "1000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.overAllocated").value(false));
        createTx(c, "EXPENSE", "7000", c.expense("Travel"), "2026-09-20", "Flights");

        postAs(c, "/api/budgets/2026-10/copy-previous?currency=INR", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total.limit").value("6000.00"))
                .andExpect(jsonPath("$.lines.length()").value(2));
        postAs(c, "/api/budgets/2026-10/copy-previous?currency=INR", null).andExpect(status().isConflict());

        JsonNode history = read(getAs(c, "/api/budgets/history?currency=INR&months=3"));
        assertThat(history).hasSize(3);
        assertThat(history.at("/0/month").asText()).isEqualTo("2026-10");
        assertThat(history.at("/1/month").asText()).isEqualTo("2026-09");
        assertThat(history.at("/1/spent").asText()).isEqualTo("7000.00");
        assertThat(history.at("/1/status").asText()).isEqualTo("EXCEEDED");
        assertThat(history.at("/1/exceededLines").asInt()).isEqualTo(1);
        assertThat(history.at("/2/status").asText()).isEqualTo("NO_LIMIT");

        deleteAs(c, "/api/budgets/2026-10?currency=INR").andExpect(status().isNoContent());
        getAs(c, "/api/budgets/2026-10?currency=INR").andExpect(jsonPath("$.planExists").value(false));
    }

    private String spent(Client c, String category) throws Exception {
        return line(read(getAs(c, "/api/budgets/2026-10?currency=INR")), category).at("/measure/spent").asText();
    }

    private static JsonNode line(JsonNode budget, String category) {
        for (JsonNode l : budget.get("lines")) {
            if (l.at("/category/name").asText().equals(category)) {
                return l;
            }
        }
        throw new AssertionError("No budget line for " + category);
    }
}
