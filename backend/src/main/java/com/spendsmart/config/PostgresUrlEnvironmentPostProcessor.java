package com.spendsmart.config;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Accepts Heroku/Render-style database URLs ({@code postgres://user:pass@host:5432/db?sslmode=require}).
 *
 * <p>Platforms like Render provide the connection string in that format, but the PostgreSQL JDBC driver only
 * understands {@code jdbc:postgresql://} URLs and rejects everything else with {@code 'url' must start with "jdbc"},
 * before Flyway ever starts. This processor rewrites {@code spring.datasource.url} at environment-post-processing
 * time: the scheme becomes {@code jdbc:postgresql://}, and credentials embedded in the URL move to
 * {@code spring.datasource.username}/{@code password} (the driver does not accept {@code user:pass@host} userinfo).
 * Query parameters such as {@code sslmode=require} are preserved.
 *
 * <p>Explicit {@code DB_USERNAME}/{@code DB_PASSWORD} (or {@code SPRING_DATASOURCE_*}) values always win over credentials
 * found in the URL. A {@code postgres://} URL with no embedded credentials (no {@code user:}@ userinfo) fails
 * startup unless those variables are set, so a misconfigured deployment stops with an actionable message instead
 * of an opaque driver authentication error. URLs that already start with {@code jdbc:} — every local, test and CI
 * setup — are untouched.
 *
 * <p>Only {@code postgres://}, {@code postgres://} and {@code jdbc:} inputs are accepted. Any other scheme (e.g.
 * {@code mysql://}) or an unparseable {@code postgres://} URL aborts startup with an actionable message that never
 * contains the URL itself, because it carries the password.
 */
public final class PostgresUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Log log = LogFactory.getLog(PostgresUrlEnvironmentPostProcessor.class);

    private static final String SET_DB_URL_MESSAGE =
            "Set DB_URL (or SPRING_DATASOURCE_URL) to a JDBC PostgreSQL URL like"
                    + " jdbc:postgresql://host:5432/database?sslmode=require, or to a postgres:// URL"
                    + " (Render/Heroku style) which this application converts automatically.";

    /** Property sources that hold operator-provided credentials, checked before falling back to URL userinfo. */
    private static final String[] EXPLICIT_USERNAME = {"DB_USERNAME", "SPRING_DATASOURCE_USERNAME"};
    private static final String[] EXPLICIT_PASSWORD = {"DB_PASSWORD", "SPRING_DATASOURCE_PASSWORD"};

    /** Runs after ConfigDataEnvironmentPostProcessor so application*.yml placeholders are already resolved. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = environment.getProperty("spring.datasource.url");
        // jdbc: URLs (local, test, CI, Docker compose) and absent values are left to Spring Boot's own handling.
        if (url == null || url.startsWith("jdbc:")) {
            return;
        }
        if (!url.startsWith("postgres://") && !url.startsWith("postgresql://")) {
            // Do not silently accept a scheme the driver will reject later with a confusing error.
            throw new IllegalStateException("spring.datasource.url must start with \"jdbc:\" (or be a postgres://"
                    + " / postgresql:// URL this application converts); got a URL with scheme \""
                    + schemeOf(url) + "\". " + SET_DB_URL_MESSAGE);
        }
        URI parsed;
        try {
            parsed = URI.create(url);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalStateException("spring.datasource.url uses the postgres:// scheme but is not a valid"
                    + " URL (the value is omitted because it contains credentials). " + SET_DB_URL_MESSAGE, malformed);
        }
        String host = parsed.getHost();
        if (host == null) {
            throw new IllegalStateException("spring.datasource.url uses the postgres:// scheme but has no host"
                    + " (the value is omitted because it contains credentials). " + SET_DB_URL_MESSAGE);
        }

        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(host);
        if (parsed.getPort() != -1) {
            jdbc.append(':').append(parsed.getPort());
        }
        // An empty path would make the driver connect to a database named after the OS user; default to "/".
        jdbc.append(parsed.getPath() == null || parsed.getPath().isEmpty() ? "/" : parsed.getPath());
        if (parsed.getQuery() != null) {
            jdbc.append('?').append(parsed.getQuery());
        }

        Map<String, Object> overrides = new HashMap<>();
        overrides.put("spring.datasource.url", jdbc.toString());
        String userInfo = parsed.getUserInfo();
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            String user = percentDecode(colon == -1 ? userInfo : userInfo.substring(0, colon));
            String password = colon == -1 ? "" : percentDecode(userInfo.substring(colon + 1));
            if (!isSet(environment, EXPLICIT_USERNAME)) {
                overrides.put("spring.datasource.username", user);
            }
            if (!isSet(environment, EXPLICIT_PASSWORD)) {
                overrides.put("spring.datasource.password", password);
            }
        } else if (!isSet(environment, EXPLICIT_USERNAME) || !isSet(environment, EXPLICIT_PASSWORD)) {
            throw new IllegalStateException("spring.datasource.url uses the postgres:// scheme but carries no"
                    + " credentials, and DB_USERNAME/DB_PASSWORD are not set either (the value is omitted because"
                    + " it may contain credentials). Use the full postgres://user:pass@host:5432/db URL from your"
                    + " database platform, or set DB_USERNAME and DB_PASSWORD explicitly.");
        }
        // Highest precedence: the converted URL must win over the postgres:// value resolved from yml/env.
        environment.getPropertySources().addFirst(new MapPropertySource("platformDatabaseUrl", overrides));
        log.info("Converted non-jdbc database URL to jdbc:postgresql://" + host
                + (parsed.getPort() == -1 ? "" : ":" + parsed.getPort())
                + (parsed.getPath() == null ? "" : parsed.getPath()));
    }

    /** The scheme of a URL, for error messages only — never the full value, which may carry credentials. */
    private static String schemeOf(String url) {
        int separator = url.indexOf("//");
        String scheme = separator == -1 ? url : url.substring(0, separator);
        scheme = scheme.substring(0, Math.min(scheme.length(), 20)).replaceAll("[^A-Za-z0-9+.-]", "");
        return scheme.isEmpty() ? "unknown" : scheme;
    }

    private static boolean isSet(ConfigurableEnvironment environment, String[] candidates) {
        for (String candidate : candidates) {
            if (environment.containsProperty(candidate) && !environment.getProperty(candidate).isBlank()) {
                return true;
            }
        }
        return false;
    }

    /** RFC 3986 percent-decoding (unlike URLDecoder, a literal "+" is not treated as a space). */
    private static String percentDecode(String value) {
        if (value.indexOf('%') == -1) {
            return value;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%' && i + 2 < value.length()) {
                int hi = Character.digit(value.charAt(i + 1), 16);
                int lo = Character.digit(value.charAt(i + 2), 16);
                if (hi != -1 && lo != -1) {
                    bytes.write((hi << 4) + lo);
                    i += 2;
                    continue;
                }
            }
            for (byte b : String.valueOf(c).getBytes(StandardCharsets.UTF_8)) {
                bytes.write(b);
            }
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
