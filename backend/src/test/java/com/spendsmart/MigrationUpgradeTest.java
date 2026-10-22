package com.spendsmart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Upgrade path, not just a fresh install: migrate a real PostgreSQL 16 to V1 only, write V1-shaped data the way the
 * V1 application did, then apply the remaining migrations and check that data survived and new constraints hold.
 */
@Testcontainers(disabledWithoutDocker = true)
class MigrationUpgradeTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void v1DatabaseUpgradesToLatestWithDataIntactAndOwnershipEnforced() {
        DriverManagerDataSource ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("1").load().migrate();
        UUID alice = user(jdbc, "alice");
        UUID bob = user(jdbc, "bob");
        UUID goal = UUID.randomUUID();
        jdbc.update("insert into savings_goals (id, user_id, name, target_amount, currency, created_at, updated_at) "
                + "values (?, ?, 'Fund', 1000, 'INR', now(), now())", goal, alice);
        jdbc.update("insert into goal_contributions (id, goal_id, user_id, type, amount, contribution_date, created_at) "
                + "values (?, ?, ?, 'DEPOSIT', 250, date '2026-10-01', now())", UUID.randomUUID(), goal, alice);

        var result = Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).isEqualTo("2");

        // Existing rows survive; existing users get the documented default zone.
        assertThat(jdbc.queryForObject("select time_zone from users where id = ?", String.class, alice)).isEqualTo("UTC");
        assertThat(jdbc.queryForObject("select sum(amount) from goal_contributions where goal_id = ?",
                java.math.BigDecimal.class, goal)).isEqualByComparingTo("250");

        // New in V2: a contribution can no longer be attached to another user's goal, even by direct SQL.
        assertThatThrownBy(() -> jdbc.update("insert into goal_contributions "
                + "(id, goal_id, user_id, type, amount, contribution_date, created_at) "
                + "values (?, ?, ?, 'DEPOSIT', 1, date '2026-10-02', now())", UUID.randomUUID(), goal, bob))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static UUID user(JdbcTemplate jdbc, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users (id, email, password_hash, display_name, default_currency, locale, theme, "
                + "created_at, updated_at) values (?, ?, '{noop}x', ?, 'INR', 'en-IN', 'SYSTEM', now(), now())",
                id, name + "-" + id + "@example.test", name);
        return id;
    }
}
