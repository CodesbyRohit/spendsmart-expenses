package com.spendsmart.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendsmart.ApiTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CategoryApiTest extends ApiTestSupport {

    @Test
    void createRenameArchiveAndListCategories() throws Exception {
        Client c = register();
        JsonNode pets = read(postAs(c, "/api/categories", Map.of("name", " Pet  care ", "type", "EXPENSE", "icon", "paw-print"))
                .andExpect(status().isCreated()));
        assertThat(pets.get("name").asText()).isEqualTo("Pet care");
        assertThat(pets.get("isDefault").asBoolean()).isFalse();

        postAs(c, "/api/categories", Map.of("name", "pet CARE", "type", "EXPENSE", "icon", "paw-print"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
        // The same name is fine for the other type.
        postAs(c, "/api/categories", Map.of("name", "Pet care", "type", "INCOME", "icon", "paw-print"))
                .andExpect(status().isCreated());
        postAs(c, "/api/categories", Map.of("name", "Bad icon", "type", "EXPENSE", "icon", "<script>"))
                .andExpect(status().isBadRequest());

        String url = "/api/categories/" + pets.get("id").asText();
        putAs(c, url, Map.of("name", "Pets", "icon", "paw-print", "archived", true)).andExpect(status().isOk());
        JsonNode active = read(getAs(c, "/api/categories"));
        JsonNode all = read(getAs(c, "/api/categories?includeArchived=true"));
        assertThat(active.toString()).doesNotContain("\"Pets\"");
        assertThat(all.toString()).contains("\"Pets\"");

        // An archived category cannot receive new transactions.
        postAs(c, "/api/transactions", tx("EXPENSE", "10", java.util.UUID.fromString(pets.get("id").asText()), TODAY, "Food"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void categoriesInUseCannotBeDeletedWithoutReassignment() throws Exception {
        Client c = register();
        createTx(c, "EXPENSE", "120", c.expense("Entertainment"), TODAY, "Cinema");
        createTx(c, "EXPENSE", "80", c.expense("Entertainment"), TODAY, "Arcade");
        putAs(c, "/api/budgets/2026-10", Map.of("currency", "INR", "lines",
                List.of(Map.of("categoryId", c.expense("Entertainment"), "limit", "500")))).andExpect(status().isOk());
        String url = "/api/categories/" + c.expense("Entertainment");

        getAs(c, url + "/usage").andExpect(jsonPath("$.transactionCount").value(2)).andExpect(jsonPath("$.budgetLineCount").value(1));
        deleteAs(c, url).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));
        deleteAs(c, url + "?reassignTo=" + c.income("Salary")).andExpect(status().isBadRequest());

        deleteAs(c, url + "?reassignTo=" + c.expense("Other")).andExpect(status().isNoContent());
        getAs(c, "/api/transactions?categoryId=" + c.expense("Other")).andExpect(jsonPath("$.totalItems").value(2));
        getAs(c, "/api/budgets/2026-10?currency=INR")
                .andExpect(jsonPath("$.lines.length()").value(0))
                .andExpect(jsonPath("$.total.spent").value("200.00"));
    }

    @Test
    void unusedCategoryCanBeDeleted() throws Exception {
        Client c = register();
        deleteAs(c, "/api/categories/" + c.expense("Travel")).andExpect(status().isNoContent());
        assertThat(read(getAs(c, "/api/categories")).toString()).doesNotContain("\"Travel\"");
    }
}
