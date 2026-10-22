package com.spendsmart.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendsmart.ApiTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Broken-access-control checks: user B must not be able to read, change or use anything user A owns. */
class CrossUserIsolationTest extends ApiTestSupport {

    @Test
    void transactionsAreInvisibleAndImmutableToOtherUsers() throws Exception {
        Client alice = register();
        Client bob = register();
        JsonNode tx = createTx(alice, "EXPENSE", "1250.00", alice.expense("Groceries"), TODAY, "Alice private groceries");
        String url = "/api/transactions/" + tx.get("id").asText();

        getAs(bob, url).andExpect(status().isNotFound());
        Map<String, Object> update = tx("EXPENSE", "1.00", bob.expense("Groceries"), TODAY, "hijacked");
        update.put("version", 0);
        putAs(bob, url, update).andExpect(status().isNotFound());
        deleteAs(bob, url).andExpect(status().isNotFound());

        getAs(bob, "/api/transactions").andExpect(jsonPath("$.totalItems").value(0));
        String csv = getAs(bob, "/api/transactions/export").andReturn().getResponse().getContentAsString();
        assertThat(csv).doesNotContain("Alice private groceries");
        getAs(bob, "/api/search?q=Alice").andExpect(jsonPath("$.transactionTotal").value(0));

        getAs(alice, url).andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Alice private groceries"));
    }

    @Test
    void otherUsersCategoriesCannotBeUsedOrModified() throws Exception {
        Client alice = register();
        Client bob = register();
        String aliceGroceries = alice.expense("Groceries").toString();

        postAs(bob, "/api/transactions", tx("EXPENSE", "10", alice.expense("Groceries"), TODAY, "sneaky"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("categoryId"));
        putAs(bob, "/api/categories/" + aliceGroceries, Map.of("name", "Mine now", "icon", "home", "archived", false))
                .andExpect(status().isNotFound());
        deleteAs(bob, "/api/categories/" + aliceGroceries).andExpect(status().isNotFound());
        getAs(bob, "/api/categories/" + aliceGroceries + "/usage").andExpect(status().isNotFound());
        // Reassigning into someone else's category is refused too.
        createTx(bob, "EXPENSE", "5", bob.expense("Shopping"), TODAY, "pen");
        deleteAs(bob, "/api/categories/" + bob.expense("Shopping") + "?reassignTo=" + aliceGroceries)
                .andExpect(status().isBadRequest());
    }

    @Test
    void budgetsAreScopedToTheirOwner() throws Exception {
        Client alice = register();
        Client bob = register();
        putAs(alice, "/api/budgets/2026-10", Map.of("currency", "INR", "totalLimit", "20000",
                "lines", List.of(Map.of("categoryId", alice.expense("Groceries"), "limit", "5000"))))
                .andExpect(status().isOk());
        createTx(alice, "EXPENSE", "3000", alice.expense("Groceries"), TODAY, "Alice groceries");

        getAs(bob, "/api/budgets/2026-10?currency=INR")
                .andExpect(jsonPath("$.planExists").value(false))
                .andExpect(jsonPath("$.total.spent").value("0.00"));
        putAs(bob, "/api/budgets/2026-10", Map.of("currency", "INR",
                "lines", List.of(Map.of("categoryId", alice.expense("Groceries"), "limit", "100"))))
                .andExpect(status().isBadRequest());
        deleteAs(bob, "/api/budgets/2026-10?currency=INR").andExpect(status().isNotFound());
        getAs(alice, "/api/budgets/2026-10?currency=INR").andExpect(jsonPath("$.planExists").value(true));
    }

    @Test
    void goalsAndContributionsAreScopedToTheirOwner() throws Exception {
        Client alice = register();
        Client bob = register();
        JsonNode goal = read(postAs(alice, "/api/goals", Map.of("name", "Laptop", "targetAmount", "80000",
                "currency", "INR", "initialAmount", "5000")).andExpect(status().isCreated()));
        String url = "/api/goals/" + goal.get("id").asText();

        getAs(bob, url).andExpect(status().isNotFound());
        getAs(bob, url + "/contributions").andExpect(status().isNotFound());
        postAs(bob, url + "/contributions", Map.of("type", "WITHDRAWAL", "amount", "5000", "date", TODAY))
                .andExpect(status().isNotFound());
        putAs(bob, url, Map.of("name", "x", "targetAmount", "1", "version", 0)).andExpect(status().isNotFound());
        deleteAs(bob, url).andExpect(status().isNotFound());
        getAs(bob, "/api/goals").andExpect(jsonPath("$.length()").value(0));

        getAs(alice, url).andExpect(jsonPath("$.savedAmount").value("5000.00"));
    }

    @Test
    void analyticsAndDashboardOnlyAggregateOwnRecords() throws Exception {
        Client alice = register();
        Client bob = register();
        createTx(alice, "INCOME", "90000", alice.income("Salary"), "2026-10-01", "Alice salary");
        createTx(alice, "EXPENSE", "4000", alice.expense("Housing"), "2026-10-02", "Alice rent");
        createTx(bob, "EXPENSE", "100", bob.expense("Groceries"), "2026-10-02", "Bob bread");

        getAs(bob, "/api/dashboard?from=2026-10-01&to=2026-10-31&currency=INR")
                .andExpect(jsonPath("$.overview.totals.income").value("0.00"))
                .andExpect(jsonPath("$.overview.totals.expenses").value("100.00"))
                .andExpect(jsonPath("$.recentTransactions.length()").value(1));
    }

    @Test
    void notificationsCannotBeReadOrModifiedByOtherUsers() throws Exception {
        Client alice = register();
        Client bob = register();
        putAs(alice, "/api/budgets/2026-10", Map.of("currency", "INR", "totalLimit", "100", "lines", List.of()))
                .andExpect(status().isOk());
        createTx(alice, "EXPENSE", "150", alice.expense("Groceries"), TODAY, "over budget");
        JsonNode list = read(getAs(alice, "/api/notifications"));
        assertThat(list.get("items")).isNotEmpty();
        String id = list.get("items").get(0).get("id").asText();

        postAs(bob, "/api/notifications/" + id + "/read", null).andExpect(status().isNotFound());
        getAs(bob, "/api/notifications").andExpect(jsonPath("$.items.length()").value(0));
    }
}
