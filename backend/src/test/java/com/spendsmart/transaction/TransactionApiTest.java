package com.spendsmart.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendsmart.ApiTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TransactionApiTest extends ApiTestSupport {

    @Test
    void fullCrudLifecycle() throws Exception {
        Client c = register();
        Map<String, Object> body = tx("EXPENSE", "249.50", c.expense("Food and Dining"), "2026-10-10", "  Team   lunch ");
        body.put("merchant", "Cafe Corner");
        body.put("paymentMethod", "UPI");
        body.put("notes", "Split with two colleagues");
        JsonNode created = read(postAs(c, "/api/transactions", body).andExpect(status().isCreated()));
        String id = created.get("id").asText();
        assertThat(created.get("amount").asText()).isEqualTo("249.50");
        assertThat(created.get("description").asText()).isEqualTo("Team lunch");
        assertThat(created.get("category").get("name").asText()).isEqualTo("Food and Dining");
        assertThat(created.get("createdAt").asText()).isNotBlank();

        getAs(c, "/api/transactions/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.merchant").value("Cafe Corner"));

        Map<String, Object> update = tx("EXPENSE", "300", c.expense("Groceries"), "2026-10-11", "Weekly shop");
        update.put("version", created.get("version").asLong());
        putAs(c, "/api/transactions/" + id, update)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("300.00"))
                .andExpect(jsonPath("$.category.name").value("Groceries"))
                .andExpect(jsonPath("$.merchant").doesNotExist());

        deleteAs(c, "/api/transactions/" + id).andExpect(status().isNoContent());
        getAs(c, "/api/transactions/" + id).andExpect(status().isNotFound());
    }

    @Test
    void staleVersionIsRejectedAsConcurrentModification() throws Exception {
        Client c = register();
        JsonNode created = createTx(c, "EXPENSE", "10", c.expense("Groceries"), TODAY, "Bread");
        Map<String, Object> update = tx("EXPENSE", "11", c.expense("Groceries"), TODAY, "Bread");
        update.put("version", created.get("version").asLong());
        putAs(c, "/api/transactions/" + created.get("id").asText(), update).andExpect(status().isOk());
        putAs(c, "/api/transactions/" + created.get("id").asText(), update)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void amountsAreValidatedAgainstCurrencyRules() throws Exception {
        Client c = register();
        expectFieldError(c, tx("EXPENSE", "0", c.expense("Groceries"), TODAY, "x"), "amount");
        expectFieldError(c, tx("EXPENSE", "-5", c.expense("Groceries"), TODAY, "x"), "amount");
        expectFieldError(c, tx("EXPENSE", "10.555", c.expense("Groceries"), TODAY, "x"), "amount");

        Map<String, Object> yen = tx("EXPENSE", "100.5", c.expense("Groceries"), TODAY, "x");
        yen.put("currency", "JPY");
        expectFieldError(c, yen, "amount");
        yen.put("amount", "100");
        postAs(c, "/api/transactions", yen).andExpect(status().isCreated()).andExpect(jsonPath("$.amount").value("100"));

        Map<String, Object> dinar = tx("EXPENSE", "1.255", c.expense("Groceries"), TODAY, "x");
        dinar.put("currency", "KWD");
        postAs(c, "/api/transactions", dinar).andExpect(status().isCreated()).andExpect(jsonPath("$.amount").value("1.255"));

        Map<String, Object> badCurrency = tx("EXPENSE", "10", c.expense("Groceries"), TODAY, "x");
        badCurrency.put("currency", "ABC");
        expectFieldError(c, badCurrency, "currency");
    }

    @Test
    void categoryMustMatchTransactionType() throws Exception {
        Client c = register();
        expectFieldError(c, tx("EXPENSE", "10", c.income("Salary"), TODAY, "x"), "categoryId");
        expectFieldError(c, tx("INCOME", "10", c.expense("Groceries"), TODAY, "x"), "categoryId");
    }

    @Test
    void requiredFieldsDatesAndUnknownPropertiesAreValidated() throws Exception {
        Client c = register();
        postAs(c, "/api/transactions", Map.of("type", "EXPENSE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(5));
        expectFieldError(c, tx("EXPENSE", "10", c.expense("Groceries"), "2030-01-01", "x"), "transactionDate");
        postAs(c, "/api/transactions", "{\"type\":\"EXPENSE\",\"amount\":\"10\",\"currency\":\"INR\",\"categoryId\":\""
                + c.expense("Groceries") + "\",\"description\":\"x\",\"transactionDate\":\"2026-02-30\"}")
                .andExpect(status().isBadRequest());
        // Mass-assignment attempt: server-controlled fields are not part of the contract.
        Map<String, Object> sneaky = tx("EXPENSE", "10", c.expense("Groceries"), TODAY, "x");
        sneaky.put("userId", c.userId());
        sneaky.put("sample", true);
        postAs(c, "/api/transactions", sneaky)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void listSupportsSearchFiltersSortingAndServerSidePagination() throws Exception {
        Client c = register();
        createTx(c, "EXPENSE", "100", "INR", c.expense("Groceries"), "2026-10-01", "Vegetables", "FreshMart");
        createTx(c, "EXPENSE", "900", "INR", c.expense("Shopping"), "2026-10-05", "Shoes", "Urban Threads");
        createTx(c, "EXPENSE", "50", "INR", c.expense("Groceries"), "2026-09-28", "Fruit 100%_pure", "FreshMart");
        createTx(c, "INCOME", "50000", "INR", c.income("Salary"), "2026-10-01", "Salary", null);
        createTx(c, "EXPENSE", "20", "USD", c.expense("Subscriptions"), "2026-10-03", "Cloud storage", null);

        getAs(c, "/api/transactions?size=2&page=0&sort=amount,desc")
                .andExpect(jsonPath("$.totalItems").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].description").value("Salary"));
        getAs(c, "/api/transactions?size=2&page=2&sort=amount,desc")
                .andExpect(jsonPath("$.items.length()").value(1));

        getAs(c, "/api/transactions?q=freshmart").andExpect(jsonPath("$.totalItems").value(2));
        // Wildcards in search text are matched literally (MockMvc percent-encodes the template itself).
        getAs(c, "/api/transactions?q=100%_").andExpect(jsonPath("$.totalItems").value(1));
        getAs(c, "/api/transactions?q=%").andExpect(jsonPath("$.totalItems").value(1));
        getAs(c, "/api/transactions?type=INCOME").andExpect(jsonPath("$.totalItems").value(1));
        getAs(c, "/api/transactions?categoryId=" + c.expense("Groceries")).andExpect(jsonPath("$.totalItems").value(2));
        getAs(c, "/api/transactions?from=2026-10-01&to=2026-10-05&currency=INR").andExpect(jsonPath("$.totalItems").value(3));
        getAs(c, "/api/transactions?sort=date,asc").andExpect(jsonPath("$.items[0].description").value("Fruit 100%_pure"));

        getAs(c, "/api/transactions?sort=userId,asc").andExpect(status().isBadRequest());
        getAs(c, "/api/transactions?size=1000").andExpect(status().isBadRequest());
        getAs(c, "/api/transactions?from=2026-10-05&to=2026-10-01").andExpect(status().isBadRequest());
    }

    @Test
    void csvExportHonoursFiltersAndNeutralisesFormulas() throws Exception {
        Client c = register();
        createTx(c, "EXPENSE", "75.25", "INR", c.expense("Groceries"), "2026-10-02", "=HYPERLINK(\"http://evil\")", "Shop, \"Main\"");
        createTx(c, "EXPENSE", "10", "INR", c.expense("Shopping"), "2026-09-02", "Old item", null);

        var result = getAs(c, "/api/transactions/export?from=2026-10-01&to=2026-10-31")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse();
        assertThat(result.getContentType()).startsWith("text/csv");
        String csv = result.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String[] lines = csv.replace("﻿", "").split("\r\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).startsWith("Date,Type,Amount,Currency,Category,Description");
        assertThat(lines[1]).startsWith("2026-10-02,EXPENSE,75.25,INR,Groceries,")
                .contains("\"'=HYPERLINK(\"\"http://evil\"\")\"")
                .contains("\"Shop, \"\"Main\"\"\"")
                .doesNotContain("Old item");
    }

    @Test
    void csvExportWorksWithTheHeadersTheBrowserAppSends() throws Exception {
        Client c = register();
        createTx(c, "EXPENSE", "12.50", c.expense("Groceries"), TODAY, "Bread");
        // Regression: with produces="text/csv", Accept: application/json (sent by the SPA client) fell through to
        // GET /{id} with id="export" and returned 400 VALIDATION_FAILED.
        for (String accept : List.of("application/json", "*/*", "text/csv")) {
            var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/api/transactions/export").session(c.session()).header("Accept", accept))
                    .andExpect(status().isOk())
                    .andReturn().getResponse();
            assertThat(response.getContentType()).as("Accept %s", accept).startsWith("text/csv");
            assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("Bread");
        }
        // Non-UUID path segments no longer reach the {id} handler as malformed ids.
        getAs(c, "/api/transactions/not-a-real-endpoint").andExpect(status().isNotFound());
    }

    private void expectFieldError(Client c, Map<String, Object> body, String field) throws Exception {
        postAs(c, "/api/transactions", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value(field));
    }
}
