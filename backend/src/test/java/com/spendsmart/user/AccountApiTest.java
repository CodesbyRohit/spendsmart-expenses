package com.spendsmart.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendsmart.ApiTestSupport;
import com.spendsmart.category.CategoryRepository;
import com.spendsmart.transaction.TransactionRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class AccountApiTest extends ApiTestSupport {

    @Autowired
    UserRepository users;
    @Autowired
    TransactionRepository transactions;
    @Autowired
    CategoryRepository categories;

    @Test
    void profileAndPreferencesCanBeUpdated() throws Exception {
        Client c = register();
        putAs(c, "/api/account/preferences", Map.of("defaultCurrency", "USD", "locale", "en-US", "theme", "DARK",
                "budgetAlertsEnabled", true, "budgetAlertThreshold", 90))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultCurrency").value("USD"))
                .andExpect(jsonPath("$.theme").value("DARK"))
                .andExpect(jsonPath("$.budgetAlertThreshold").value(90));
        putAs(c, "/api/account/preferences", Map.of("defaultCurrency", "USD", "locale", "xx-XX", "theme", "DARK",
                "budgetAlertsEnabled", true, "budgetAlertThreshold", 20)).andExpect(status().isBadRequest());

        putAs(c, "/api/account/profile", Map.of("displayName", "Renamed", "email", c.email()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Renamed"));
        // Changing the email needs the current password.
        putAs(c, "/api/account/profile", Map.of("displayName", "Renamed", "email", "new-" + c.email()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("currentPassword"));
        putAs(c, "/api/account/profile", Map.of("displayName", "Renamed", "email", "new-" + c.email(),
                "currentPassword", PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void accountExportContainsOwnDataButNoSecrets() throws Exception {
        Client c = register();
        createTx(c, "EXPENSE", "42", c.expense("Groceries"), TODAY, "Exported groceries");
        postAs(c, "/api/goals", Map.of("name", "Export goal", "targetAmount", "100", "currency", "INR", "initialAmount", "10"));
        String body = getAs(c, "/api/account/export").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode export = json.readTree(body);
        assertThat(export.get("format").asText()).isEqualTo("spendsmart-account-export/v1");
        assertThat(export.get("transactions")).hasSize(1);
        assertThat(export.at("/savingsGoals/0/contributions")).hasSize(1);
        assertThat(body).doesNotContain("passwordHash").doesNotContain("$2a$").doesNotContain("credentialVersion");
    }

    @Test
    void accountDeletionRequiresPasswordAndConfirmationThenRemovesEverything() throws Exception {
        Client c = register();
        createTx(c, "EXPENSE", "42", c.expense("Groceries"), TODAY, "Soon gone");
        putAs(c, "/api/budgets/2026-10", Map.of("currency", "INR", "totalLimit", "10", "lines",
                List.of(Map.of("categoryId", c.expense("Groceries"), "limit", "5"))));
        postAs(c, "/api/goals", Map.of("name", "Gone", "targetAmount", "100", "currency", "INR", "initialAmount", "10"));

        postAs(c, "/api/account/delete", Map.of("password", PASSWORD, "confirmation", "delete"))
                .andExpect(status().isBadRequest());
        postAs(c, "/api/account/delete", Map.of("password", "wrong-password!", "confirmation", "DELETE"))
                .andExpect(status().isBadRequest());
        assertThat(users.existsById(c.userId())).isTrue();

        postAs(c, "/api/account/delete", Map.of("password", PASSWORD, "confirmation", "DELETE"))
                .andExpect(status().isNoContent());
        assertThat(users.existsById(c.userId())).isFalse();
        assertThat(transactions.existsByUserId(c.userId())).isFalse();
        assertThat(categories.existsByUserId(c.userId())).isFalse();
        getAs(c, "/api/auth/me").andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", c.email(), "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }
}
