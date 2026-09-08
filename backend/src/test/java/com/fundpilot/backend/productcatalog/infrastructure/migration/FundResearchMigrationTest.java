package com.fundpilot.backend.productcatalog.infrastructure.migration;

import com.fundpilot.backend.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class FundResearchMigrationTest extends AbstractIntegrationTest {
    private static final String SCHEMA = "flyway_fund_research_test";
    @Autowired DataSource dataSource;

    @Test
    void createsNullableResearchSnapshotsAndExtendsFeeFacts() throws Exception {
        execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        execute("CREATE SCHEMA " + SCHEMA);
        try {
            flyway().target(MigrationVersion.fromVersion("51")).load().migrate();
            Flyway flyway = flyway().target(MigrationVersion.fromVersion("52")).load();

            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            execute("INSERT INTO " + SCHEMA + ".fund_research (fund_code) VALUES ('005919')");
            execute("INSERT INTO " + SCHEMA + ".fund_fee "
                    + "(fund_code, fetched_at) VALUES ('005919', now())");

            assertThat(count("SELECT count(*) FROM " + SCHEMA
                    + ".fund_research WHERE profile_data IS NULL AND scale_data IS NULL "
                    + "AND holdings_data IS NULL")).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM " + SCHEMA
                    + ".fund_fee WHERE management_fee IS NULL AND custody_fee IS NULL "
                    + "AND refresh_status = 'SUCCESS'")).isEqualTo(1);
            assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        } finally {
            execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    private org.flywaydb.core.api.configuration.FluentConfiguration flyway() {
        return Flyway.configure().dataSource(dataSource).schemas(SCHEMA).defaultSchema(SCHEMA)
                .locations("classpath:db/migration");
    }

    private long count(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
