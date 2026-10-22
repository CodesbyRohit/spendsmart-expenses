package com.spendsmart.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Regression test for a reproduced weakness: with {@code server.forward-headers-strategy=native}, Tomcat trusted
 * X-Forwarded-For from any private-network peer (which includes every client of a Docker Compose deployment), so a
 * client could rotate a fake header to bypass per-IP rate limits. Runs the real prod profile on a real port, because
 * MockMvc does not exercise Tomcat's header handling.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "prod"})
@TestPropertySource(properties = {
        "DB_URL=jdbc:h2:mem:xff-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "DB_USERNAME=sa",
        "DB_PASSWORD=",
        "app.security.registration-per-ip-per-hour=2"})
class ForwardedHeaderSpoofingTest {

    @LocalServerPort
    int port;

    private final CookieManager cookies = new CookieManager();
    private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();

    @Test
    void spoofedForwardedForDoesNotResetThePerIpRegistrationLimit() throws Exception {
        assertThat(register(null)).isEqualTo(201);
        assertThat(register(null)).isEqualTo(201);
        assertThat(register(null)).isEqualTo(429);
        // Before the fix each of these returned 201: the fake header was taken as the client address.
        for (String fakeIp : List.of("203.0.113.1", "203.0.113.2", "198.51.100.7")) {
            assertThat(register(fakeIp)).as("registration with X-Forwarded-For %s", fakeIp).isEqualTo(429);
        }
    }

    private int register(String forwardedFor) throws Exception {
        String base = "http://localhost:" + port;
        http.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        String token = cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("XSRF-TOKEN"))
                .map(HttpCookie::getValue)
                .reduce((first, latest) -> latest)
                .orElseThrow();
        String body = "{\"email\":\"xff-" + UUID.randomUUID() + "@example.test\",\"password\":\"long-enough-password\","
                + "\"displayName\":\"X\",\"defaultCurrency\":\"INR\",\"locale\":\"en-IN\"}";
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + "/api/auth/register"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", token)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
