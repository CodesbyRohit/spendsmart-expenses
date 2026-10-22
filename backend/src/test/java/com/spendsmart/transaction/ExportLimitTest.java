package com.spendsmart.transaction;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spendsmart.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** The export cap is enforced with a COUNT before any row is loaded; filters that narrow the result still work. */
@TestPropertySource(properties = "app.export.max-rows=2")
class ExportLimitTest extends ApiTestSupport {

    @Test
    void oversizedExportIsRefusedAndNarrowerExportSucceeds() throws Exception {
        Client c = register();
        createTx(c, "EXPENSE", "10", c.expense("Groceries"), "2026-10-01", "One");
        createTx(c, "EXPENSE", "20", c.expense("Groceries"), "2026-10-02", "Two");
        createTx(c, "EXPENSE", "30", c.expense("Groceries"), "2026-10-03", "Three");

        getAs(c, "/api/transactions/export")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXPORT_TOO_LARGE"));
        getAs(c, "/api/transactions/export?from=2026-10-02&to=2026-10-03").andExpect(status().isOk());
    }
}
