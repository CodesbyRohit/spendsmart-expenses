package com.spendsmart.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendsmart.ApiTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GoalApiTest extends ApiTestSupport {

    @Test
    void progressIsTheSumOfContributions() throws Exception {
        Client c = register();
        JsonNode goal = read(postAs(c, "/api/goals", Map.of("name", "Emergency fund", "targetAmount", "10000",
                "currency", "INR", "targetDate", "2027-03-31", "initialAmount", "2500")).andExpect(status().isCreated()));
        String url = "/api/goals/" + goal.get("id").asText();
        assertThat(goal.get("savedAmount").asText()).isEqualTo("2500.00");
        assertThat(goal.get("progressPercent").asText()).isEqualTo("25.0");
        assertThat(goal.get("remainingAmount").asText()).isEqualTo("7500.00");
        // Oct 15 → Mar 31 = 5 whole months + the current partial one = 6 months; 7500 / 6 = 1250.
        assertThat(goal.get("monthlyNeeded").asText()).isEqualTo("1250.00");

        postAs(c, url + "/contributions", Map.of("type", "DEPOSIT", "amount", "1000", "date", TODAY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.savedAmount").value("3500.00"));
        postAs(c, url + "/contributions", Map.of("type", "WITHDRAWAL", "amount", "500", "date", TODAY, "note", "Repair"))
                .andExpect(jsonPath("$.savedAmount").value("3000.00"))
                .andExpect(jsonPath("$.contributionCount").value(3));

        JsonNode history = read(getAs(c, url + "/contributions"));
        assertThat(history).hasSize(3);
        assertThat(history.findValuesAsText("type")).containsExactlyInAnyOrder("DEPOSIT", "DEPOSIT", "WITHDRAWAL");
    }

    @Test
    void withdrawalsCannotExceedSavedAmount() throws Exception {
        Client c = register();
        JsonNode goal = read(postAs(c, "/api/goals", Map.of("name", "Bike", "targetAmount", "5000", "currency", "INR",
                "initialAmount", "100")));
        postAs(c, "/api/goals/" + goal.get("id").asText() + "/contributions",
                Map.of("type", "WITHDRAWAL", "amount", "100.01", "date", TODAY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));
    }

    @Test
    void deletingADepositCannotMakeSavingsNegative() throws Exception {
        Client c = register();
        JsonNode goal = read(postAs(c, "/api/goals", Map.of("name", "Trip", "targetAmount", "5000", "currency", "INR",
                "initialAmount", "1000")));
        String url = "/api/goals/" + goal.get("id").asText();
        postAs(c, url + "/contributions", Map.of("type", "WITHDRAWAL", "amount", "800", "date", TODAY));
        JsonNode deposit = null;
        for (JsonNode entry : read(getAs(c, url + "/contributions"))) {
            if (entry.get("type").asText().equals("DEPOSIT")) {
                deposit = entry;
            }
        }
        deleteAs(c, url + "/contributions/" + deposit.get("id").asText())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GOAL_WOULD_GO_NEGATIVE"));
    }

    @Test
    void completedGoalsReportOverHundredPercentWithClampedBar() throws Exception {
        Client c = register();
        JsonNode goal = read(postAs(c, "/api/goals", Map.of("name", "Phone", "targetAmount", "1000", "currency", "INR",
                "initialAmount", "1200")));
        assertThat(goal.get("completed").asBoolean()).isTrue();
        assertThat(goal.get("progressPercent").asText()).isEqualTo("120.0");
        assertThat(goal.get("barPercent").asText()).isEqualTo("100.0");
        assertThat(goal.get("remainingAmount").asText()).isEqualTo("0.00");
    }

    @Test
    void editingAndDeletingGoals() throws Exception {
        Client c = register();
        JsonNode goal = read(postAs(c, "/api/goals", Map.of("name", "Course", "targetAmount", "3000", "currency", "INR")));
        String url = "/api/goals/" + goal.get("id").asText();
        putAs(c, url, Map.of("name", "Online course", "targetAmount", "3500", "version", goal.get("version").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Online course"))
                .andExpect(jsonPath("$.targetAmount").value("3500.00"));
        putAs(c, url, Map.of("name", "Stale", "targetAmount", "1", "version", goal.get("version").asLong()))
                .andExpect(status().isConflict());
        deleteAs(c, url).andExpect(status().isNoContent());
        getAs(c, url).andExpect(status().isNotFound());
    }

    @Test
    void validatesMonetaryValuesAndDates() throws Exception {
        Client c = register();
        postAs(c, "/api/goals", Map.of("name", "X", "targetAmount", "0", "currency", "INR"))
                .andExpect(status().isBadRequest());
        postAs(c, "/api/goals", Map.of("name", "X", "targetAmount", "10.001", "currency", "INR"))
                .andExpect(status().isBadRequest());
        JsonNode goal = read(postAs(c, "/api/goals", Map.of("name", "X", "targetAmount", "100", "currency", "INR")));
        postAs(c, "/api/goals/" + goal.get("id").asText() + "/contributions",
                Map.of("type", "DEPOSIT", "amount", "10", "date", "2026-12-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("date"));
    }
}
