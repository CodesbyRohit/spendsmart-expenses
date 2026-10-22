package com.spendsmart;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Guards the "-Ppostgres" profile: if the suite is meant to run on PostgreSQL but the datasource is actually H2
 * (e.g. properties not reaching the forked test JVM), this fails instead of letting H2 results pass as PostgreSQL ones.
 */
@SpringBootTest
@ActiveProfiles("test")
class DatabaseEngineTest {

    @Autowired
    DataSource dataSource;

    @Test
    void suiteRunsOnTheExpectedDatabaseEngine() throws Exception {
        String expected = System.getProperty("spendsmart.test.expected-database", "H2");
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            String version = connection.getMetaData().getDatabaseProductVersion();
            System.out.println("[DatabaseEngineTest] tests are running on " + product + " " + version);
            assertThat(product).isEqualTo(expected);
        }
    }
}
