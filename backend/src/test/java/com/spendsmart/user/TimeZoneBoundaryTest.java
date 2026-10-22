package com.spendsmart.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendsmart.ApiTestSupport;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * "Today" must follow the user's time zone, not the server's. The clock is pinned to 2026-10-31T20:00Z:
 * still 31 October in UTC, but already 1 November (01:30) in Asia/Kolkata (UTC+05:30).
 */
@TestPropertySource(properties = "app.clock.fixed=2026-10-31T20:00:00Z")
class TimeZoneBoundaryTest extends ApiTestSupport {

    private Client registerIn(String zone) throws Exception {
        String email = "tz-" + UUID.randomUUID() + "@example.test";
        Map<String, Object> body = new HashMap<>(Map.of("email", email, "password", PASSWORD, "displayName", "TZ",
                "defaultCurrency", "INR", "locale", "en-IN"));
        if (zone != null) {
            body.put("timeZone", zone);
        }
        MvcResult result = mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        JsonNode user = json.readTree(result.getResponse().getContentAsString());
        Map<String, UUID> categories = new HashMap<>();
        read(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/categories")
                .session(session))).forEach(c -> categories.put(c.get("type").asText() + ":" + c.get("name").asText(),
                UUID.fromString(c.get("id").asText())));
        return new Client(session, email, UUID.fromString(user.get("id").asText()), categories);
    }

    @Test
    void registrationStoresTheBrowserZoneAndFallsBackToUtc() throws Exception {
        getAs(registerIn("Asia/Kolkata"), "/api/auth/me").andExpect(jsonPath("$.timeZone").value("Asia/Kolkata"));
        getAs(registerIn(null), "/api/auth/me").andExpect(jsonPath("$.timeZone").value("UTC"));
        // An unusable browser value must not block sign-up; it falls back to UTC.
        getAs(registerIn("Not/AZone"), "/api/auth/me").andExpect(jsonPath("$.timeZone").value("UTC"));
    }

    @Test
    void contributionDatedLocalTodayIsAcceptedEvenWhenTheServerIsStillOnYesterday() throws Exception {
        Client india = registerIn("Asia/Kolkata");
        Client utc = registerIn("UTC");
        Map<String, Object> contribution = Map.of("type", "DEPOSIT", "amount", "500", "date", "2026-11-01");

        JsonNode indiaGoal = read(postAs(india, "/api/goals", Map.of("name", "Fund", "targetAmount", "1000", "currency", "INR")));
        postAs(india, "/api/goals/" + indiaGoal.get("id").asText() + "/contributions", contribution)
                .andExpect(status().isCreated());

        JsonNode utcGoal = read(postAs(utc, "/api/goals", Map.of("name", "Fund", "targetAmount", "1000", "currency", "INR")));
        postAs(utc, "/api/goals/" + utcGoal.get("id").asText() + "/contributions", contribution)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("date"));
    }

    @Test
    void currentMonthForBudgetsDashboardAndHistoryFollowsTheUsersZone() throws Exception {
        Client india = registerIn("Asia/Kolkata");
        Client utc = registerIn("UTC");

        getAs(india, "/api/dashboard").andExpect(jsonPath("$.overview.range.from").value("2026-11-01"));
        getAs(utc, "/api/dashboard").andExpect(jsonPath("$.overview.range.from").value("2026-10-01"));

        putAs(india, "/api/budgets/2026-11", Map.of("currency", "INR", "totalLimit", "3000", "lines", List.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysLeft").value(30))
                .andExpect(jsonPath("$.dailyAllowance").value("100.00"));
        putAs(utc, "/api/budgets/2026-10", Map.of("currency", "INR", "totalLimit", "3000", "lines", List.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysLeft").value(1));

        getAs(india, "/api/budgets/history?currency=INR&months=2").andExpect(jsonPath("$[0].month").value("2026-11"));
        getAs(utc, "/api/budgets/history?currency=INR&months=2").andExpect(jsonPath("$[0].month").value("2026-10"));
    }

    @Test
    void budgetAlertsFireForTheUsersCurrentMonth() throws Exception {
        Client india = registerIn("Asia/Kolkata");
        putAs(india, "/api/budgets/2026-11", Map.of("currency", "INR", "totalLimit", "100", "lines", List.of()))
                .andExpect(status().isOk());
        createTx(india, "EXPENSE", "150", india.expense("Groceries"), "2026-11-01", "First of the month");
        JsonNode alerts = read(getAs(india, "/api/notifications"));
        assertThat(alerts.get("items")).hasSize(1);
        assertThat(alerts.at("/items/0/type").asText()).isEqualTo("BUDGET_EXCEEDED");
    }

    @Test
    void timeZoneCanBeChangedInPreferencesAndIsValidated() throws Exception {
        Client c = registerIn("UTC");
        putAs(c, "/api/account/preferences", Map.of("defaultCurrency", "INR", "locale", "en-IN", "theme", "SYSTEM",
                "budgetAlertsEnabled", true, "budgetAlertThreshold", 80, "timeZone", "Asia/Kolkata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Asia/Kolkata"));
        putAs(c, "/api/account/preferences", Map.of("defaultCurrency", "INR", "locale", "en-IN", "theme", "SYSTEM",
                "budgetAlertsEnabled", true, "budgetAlertThreshold", 80, "timeZone", "Mars/Olympus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("timeZone"));
        // Omitting the zone leaves it unchanged (older clients keep working).
        putAs(c, "/api/account/preferences", Map.of("defaultCurrency", "INR", "locale", "en-IN", "theme", "SYSTEM",
                "budgetAlertsEnabled", true, "budgetAlertThreshold", 80))
                .andExpect(jsonPath("$.timeZone").value("Asia/Kolkata"));
        getAs(c, "/api/account/options").andExpect(jsonPath("$.timeZones").isArray());
    }
}
