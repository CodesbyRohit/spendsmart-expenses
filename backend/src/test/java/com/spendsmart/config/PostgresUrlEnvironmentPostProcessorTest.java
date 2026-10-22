package com.spendsmart.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.SpringFactoriesLoader;

/**
 * Regression tests for the Render/Heroku-style {@code postgres://} URL handling. Before this processor existed,
 * pointing {@code DB_URL} (or {@code SPRING_DATASOURCE_URL}) at Render's {@code DATABASE_URL} made startup fail
 * with {@code java.lang.IllegalArgumentException: 'url' must start with "jdbc"}.
 */
class PostgresUrlEnvironmentPostProcessorTest {

    private final PostgresUrlEnvironmentPostProcessor processor = new PostgresUrlEnvironmentPostProcessor();
    private final SpringApplication none = new SpringApplication();

    private StandardEnvironment environmentWith(String... pairs) {
        StandardEnvironment environment = new StandardEnvironment();
        MapPropertySource source = new MapPropertySource("test", new HashMap<>());
        for (int i = 0; i < pairs.length; i += 2) {
            source.getSource().put(pairs[i], pairs[i + 1]);
        }
        environment.getPropertySources().addFirst(source);
        return environment;
    }

    @Test
    void processorIsRegisteredWithSpringBoot() {
        assertThat(SpringFactoriesLoader.loadFactoryNames(EnvironmentPostProcessor.class, getClass().getClassLoader()))
                .contains(PostgresUrlEnvironmentPostProcessor.class.getName());
    }

    @Test
    void renderDatabaseUrlBecomesJdbcUrlWithCredentialsExtracted() {
        StandardEnvironment environment = environmentWith(
                "spring.datasource.url",
                "postgres://spendsmart_user:s3cret%40pass@roundhouse.proxy.rlwy.net:55432/railway?sslmode=require");
        processor.postProcessEnvironment(environment, none);
        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://roundhouse.proxy.rlwy.net:55432/railway?sslmode=require");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("spendsmart_user");
        // The percent-encoded "@" in the password must be decoded, not kept literal.
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("s3cret@pass");
    }

    @Test
    void postgresqlSchemeIsAlsoConverted() {
        StandardEnvironment environment = environmentWith(
                "spring.datasource.url", "postgresql://spend_user:spend_pass@db.example.com:5432/spendsmart");
        processor.postProcessEnvironment(environment, none);
        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://db.example.com:5432/spendsmart");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("spend_user");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("spend_pass");
    }

    @Test
    void explicitCredentialsWinOverUrlUserInfo() {
        // Mimics application-prod.yml resolving ${DB_USERNAME}/${DB_PASSWORD}: both the env vars and the resolved
        // spring.datasource.* values exist, and neither may be replaced by the URL's userinfo.
        StandardEnvironment environment = environmentWith(
                "spring.datasource.url", "postgres://urluser:urlpass@host.example.com:5432/spendsmart",
                "DB_USERNAME", "explicit_user",
                "DB_PASSWORD", "explicit_pass",
                "spring.datasource.username", "explicit_user",
                "spring.datasource.password", "explicit_pass");
        processor.postProcessEnvironment(environment, none);
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("explicit_user");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("explicit_pass");
    }

    @Test
    void jdbcUrlsAreLeftUntouched() {
        String jdbcUrl = "jdbc:postgresql://localhost:5432/spendsmart?sslmode=disable";
        StandardEnvironment environment = environmentWith("spring.datasource.url", jdbcUrl);
        processor.postProcessEnvironment(environment, none);
        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo(jdbcUrl);
        assertThat(environment.getProperty("spring.datasource.username")).isNull();
        // No override source may be added for jdbc: URLs.
        assertThat(environment.getPropertySources().contains("platformDatabaseUrl")).isFalse();
    }

    @Test
    void urlWithoutPathGetsDefaultDatabasePath() {
        // No userinfo in the URL, so credentials must come from explicit variables (Neon/Heroku style when the
        // operator splits them out).
        StandardEnvironment environment = environmentWith(
                "spring.datasource.url", "postgres://db.example.com",
                "DB_USERNAME", "explicit_user",
                "DB_PASSWORD", "explicit_pass");
        processor.postProcessEnvironment(environment, none);
        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://db.example.com/");
        // Explicit DB_USERNAME/DB_PASSWORD are respected but not copied: application-prod.yml binds them via
        // ${DB_USERNAME:}/${DB_PASSWORD:}, so the processor must not write spring.datasource.username/password.
        assertThat(environment.getProperty("spring.datasource.username")).isNull();
        assertThat(environment.getProperty("spring.datasource.password")).isNull();
        assertThat(environment.getProperty("DB_USERNAME")).isEqualTo("explicit_user");
    }

    @Test
    void postgresUrlWithoutCredentialsFailsFastWhenNoVariablesAreSet() {
        StandardEnvironment environment = environmentWith(
                "spring.datasource.url", "postgres://db.example.com:5432/spendsmart");
        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, none))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("carries no")
                .hasMessageContaining("DB_USERNAME")
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void absentUrlIsLeftToSpringBoot() {
        StandardEnvironment empty = new StandardEnvironment();
        processor.postProcessEnvironment(empty, none); // must not throw
        assertThat(empty.getPropertySources().contains("platformDatabaseUrl")).isFalse();
    }

    @Test
    void unsupportedSchemeFailsFastWithoutLeakingCredentials() {
        StandardEnvironment environment = environmentWith(
                "spring.datasource.url", "mysql://user:topsecret@db.example.com:3306/spendsmart");
        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, none))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mysql")
                .hasMessageContaining("jdbc:postgresql://")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("topsecret"));
    }

    @Test
    void malformedPostgresUrlFailsFastWithoutLeakingCredentials() {
        StandardEnvironment environment = environmentWith(
                "spring.datasource.url", "postgres://user:topsecret@ho st:5432/db");
        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, none))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a valid URL")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("topsecret"));
    }

    @Test
    void postgresUrlWithoutHostFailsFast() {
        StandardEnvironment environment = environmentWith("spring.datasource.url", "postgres:///spendsmart");
        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, none))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no host");
    }

    @Test
    void literalPlusInPasswordIsPreserved() {
        StandardEnvironment environment = environmentWith(
                "spring.datasource.url", "postgres://user:p+ss@host.example.com:5432/db");
        processor.postProcessEnvironment(environment, none);
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("p+ss");
    }

    @Test
    void springBootBinderSeesConvertedDatasourceProperties() throws java.sql.SQLException {
        // Verifies the real binding path DataSourceAutoConfiguration uses (Binder -> DataSourceProperties), not just
        // string rewriting: after post-processing, Spring Boot itself must resolve a connectable PostgreSQL config.
        StandardEnvironment environment = environmentWith(
                "DB_URL", "postgres://render_user:render_pass@db.example.com:5432/railway?sslmode=require",
                "spring.datasource.url", "${DB_URL}");
        processor.postProcessEnvironment(environment, none);
        DataSourceProperties properties = Binder.get(environment)
                .bind("spring.datasource", DataSourceProperties.class).get();
        assertThat(properties.determineUrl()).startsWith("jdbc:postgresql://db.example.com:5432/railway");
        assertThat(properties.determineUsername()).isEqualTo("render_user");
        assertThat(properties.determinePassword()).isEqualTo("render_pass");
        // The real pgjdbc driver must accept the URL — with the production failure, DriverManager rejected it
        // before Flyway could start. (determineDriverClassName is not asserted: -Ppostgres overrides the driver
        // class with the Testcontainers one via system properties.)
        assertThat(java.sql.DriverManager.getDriver(properties.determineUrl()).getClass().getName())
                .contains("postgresql");
    }
}
