package com.spendsmart;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Full-stack test base: real Spring context, real security filter chain, H2 database migrated by Flyway.
 * Each test registers fresh users with random emails, so tests never share data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestSupport {

    public static final String PASSWORD = "correct-horse-battery";
    /** Matches app.clock.fixed in application-test.yml. */
    public static final String TODAY = "2026-10-15";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    /** A signed-in browser: its session plus the user's default categories by "TYPE:name". */
    public record Client(MockHttpSession session, String email, UUID userId, Map<String, UUID> categories) {
        public UUID category(String type, String name) {
            UUID id = categories.get(type + ":" + name);
            if (id == null) {
                throw new IllegalArgumentException("No category " + type + ":" + name);
            }
            return id;
        }

        public UUID expense(String name) {
            return category("EXPENSE", name);
        }

        public UUID income(String name) {
            return category("INCOME", name);
        }
    }

    protected Client register() throws Exception {
        return register("INR");
    }

    protected Client register(String currency) throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.test";
        MvcResult result = mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", email, "password", PASSWORD, "displayName", "Test User",
                                "defaultCurrency", currency, "locale", "en-IN"))))
                .andExpect(status().isCreated())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        UUID id = UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
        Map<String, UUID> categories = new HashMap<>();
        JsonNode list = read(mvc.perform(get("/api/categories").session(session)).andExpect(status().isOk()));
        list.forEach(c -> categories.put(c.get("type").asText() + ":" + c.get("name").asText(),
                UUID.fromString(c.get("id").asText())));
        return new Client(session, email, id, categories);
    }

    protected ResultActions getAs(Client c, String url) throws Exception {
        return mvc.perform(get(url).session(c.session()));
    }

    protected ResultActions postAs(Client c, String url, Object body) throws Exception {
        return mvc.perform(withBody(post(url), body).session(c.session()).with(csrf()));
    }

    protected ResultActions putAs(Client c, String url, Object body) throws Exception {
        return mvc.perform(withBody(put(url), body).session(c.session()).with(csrf()));
    }

    protected ResultActions deleteAs(Client c, String url) throws Exception {
        return mvc.perform(delete(url).session(c.session()).with(csrf()));
    }

    private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        if (body == null) {
            return builder;
        }
        return builder.contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String s ? s : json.writeValueAsString(body));
    }

    protected JsonNode read(ResultActions actions) throws Exception {
        String content = actions.andReturn().getResponse().getContentAsString();
        return content.isEmpty() ? json.nullNode() : json.readTree(content);
    }

    /** Creates a transaction and returns its JSON. */
    protected JsonNode createTx(Client c, String type, String amount, UUID categoryId, String date, String description)
            throws Exception {
        return createTx(c, type, amount, "INR", categoryId, date, description, null);
    }

    protected JsonNode createTx(Client c, String type, String amount, String currency, UUID categoryId, String date,
                                String description, String merchant) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("type", type);
        body.put("amount", amount);
        body.put("currency", currency);
        body.put("categoryId", categoryId);
        body.put("transactionDate", date);
        body.put("description", description);
        if (merchant != null) {
            body.put("merchant", merchant);
        }
        return read(postAs(c, "/api/transactions", body).andExpect(status().isCreated()));
    }

    protected static Map<String, Object> tx(String type, String amount, UUID categoryId, String date, String description) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", type);
        body.put("amount", amount);
        body.put("currency", "INR");
        body.put("categoryId", categoryId);
        body.put("transactionDate", date);
        body.put("description", description);
        return body;
    }
}
