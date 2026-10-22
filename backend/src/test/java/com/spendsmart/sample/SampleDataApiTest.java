package com.spendsmart.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendsmart.ApiTestSupport;
import org.junit.jupiter.api.Test;

class SampleDataApiTest extends ApiTestSupport {

    @Test
    void sampleDataIsFlaggedNeverInTheFutureAndRemovable() throws Exception {
        Client c = register();
        JsonNode real = createTx(c, "EXPENSE", "99", c.expense("Groceries"), TODAY, "My real purchase");

        JsonNode summary = read(postAs(c, "/api/sample-data", null).andExpect(status().isCreated()));
        assertThat(summary.get("transactions").asInt()).isGreaterThan(40);
        postAs(c, "/api/sample-data", null).andExpect(status().isConflict());

        JsonNode dashboard = read(getAs(c, "/api/dashboard"));
        assertThat(dashboard.get("sampleDataPresent").asBoolean()).isTrue();
        assertThat(dashboard.at("/budget/sample").asBoolean()).isTrue();

        JsonNode page = read(getAs(c, "/api/transactions?size=100&sort=date,desc"));
        assertThat(page.at("/items/0/transactionDate").asText()).isLessThanOrEqualTo(TODAY);
        long samples = 0;
        for (JsonNode t : page.get("items")) {
            if (t.get("sample").asBoolean()) {
                samples++;
            }
        }
        assertThat(samples).isEqualTo(summary.get("transactions").asLong());
        String csv = getAs(c, "/api/transactions/export").andReturn().getResponse().getContentAsString();
        assertThat(csv).contains("Sample data").contains(",yes,");

        deleteAs(c, "/api/sample-data").andExpect(status().isNoContent());
        getAs(c, "/api/transactions").andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(real.get("id").asText()));
        getAs(c, "/api/goals").andExpect(jsonPath("$.length()").value(0));
        getAs(c, "/api/sample-data").andExpect(jsonPath("$.present").value(false));
    }
}
