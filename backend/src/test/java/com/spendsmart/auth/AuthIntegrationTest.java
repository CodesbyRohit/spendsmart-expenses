package com.spendsmart.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spendsmart.ApiTestSupport;
import com.spendsmart.user.UserRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class AuthIntegrationTest extends ApiTestSupport {

    @Autowired
    UserRepository users;

    @Test
    void registrationHashesPasswordWithBcryptAndNeverReturnsIt() throws Exception {
        Client c = register();
        String hash = users.findById(c.userId()).orElseThrow().getPasswordHash();
        assertThat(hash).startsWith("{bcrypt}$2").doesNotContain(PASSWORD);

        String me = getAs(c, "/api/auth/me").andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(c.email()))
                .andReturn().getResponse().getContentAsString();
        assertThat(me).doesNotContainIgnoringCase("password").doesNotContain("credentialVersion");
    }

    @Test
    void registrationSeedsDefaultCategoriesInOneStep() throws Exception {
        Client c = register();
        assertThat(c.categories()).hasSize(15)
                .containsKeys("EXPENSE:Food and Dining", "EXPENSE:Subscriptions", "INCOME:Salary",
                        "INCOME:Freelance Income", "INCOME:Other", "EXPENSE:Other");
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        Client c = register();
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", c.email().toUpperCase(), "password", PASSWORD,
                                "displayName", "Dup", "defaultCurrency", "INR", "locale", "en-IN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    @Test
    void weakAndInvalidRegistrationsAreRejectedWithFieldErrors() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "short@example.test", "password", "short",
                                "displayName", "X", "defaultCurrency", "INR", "locale", "en-IN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));

        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "not-an-email", "password", PASSWORD,
                                "displayName", "X", "defaultCurrency", "XYZ", "locale", "en-IN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginWithWrongPasswordGivesGenericErrorAndUnknownEmailLooksIdentical() throws Exception {
        Client c = register();
        String wrongPassword = login(c.email(), "wrong-password-123", 401);
        String unknownUser = login("nobody-" + UUID.randomUUID() + "@example.test", "wrong-password-123", 401);
        assertThat(json.readTree(wrongPassword).get("detail").asText())
                .isEqualTo(json.readTree(unknownUser).get("detail").asText())
                .isEqualTo("Email or password is incorrect.");
    }

    @Test
    void repeatedFailedLoginsAreRateLimited() throws Exception {
        Client c = register();
        for (int i = 0; i < 5; i++) {
            login(c.email(), "wrong-password-" + i, 401);
        }
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", c.email(), "password", PASSWORD))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void protectedEndpointsRequireAuthenticationAndReturnJson() throws Exception {
        mvc.perform(get("/api/transactions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void stateChangingRequestsWithoutCsrfTokenAreRejected() throws Exception {
        Client c = register();
        mvc.perform(post("/api/transactions").session(c.session()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(tx("EXPENSE", "10", c.expense("Groceries"), TODAY, "Milk"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", c.email(), "password", PASSWORD))))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginRotatesSessionAndLogoutEndsIt() throws Exception {
        Client c = register();
        MockHttpSession anonymous = new MockHttpSession();
        String before = anonymous.getId();
        MvcResult login = mvc.perform(post("/api/auth/login").session(anonymous).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", c.email(), "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session.getId()).isNotEqualTo(before);

        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void changingPasswordSignsOutOtherSessionsButKeepsCurrentOne() throws Exception {
        Client c = register();
        MvcResult second = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", c.email(), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession otherBrowser = (MockHttpSession) second.getRequest().getSession(false);

        postAs(c, "/api/account/password", Map.of("currentPassword", PASSWORD, "newPassword", "a-brand-new-passphrase"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").session(otherBrowser)).andExpect(status().isUnauthorized());
        getAs(c, "/api/auth/me").andExpect(status().isOk());
        login(c.email(), PASSWORD, 401);
        login(c.email(), "a-brand-new-passphrase", 200);
    }

    @Test
    void passwordChangeRequiresCorrectCurrentPassword() throws Exception {
        Client c = register();
        postAs(c, "/api/account/password", Map.of("currentPassword", "not-my-password", "newPassword", "another-long-one"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("currentPassword"));
    }

    private String login(String email, String password, int expectedStatus) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }
}
