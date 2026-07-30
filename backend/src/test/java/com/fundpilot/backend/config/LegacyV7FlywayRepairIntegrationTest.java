package com.fundpilot.backend.platform.persistence.flyway;

import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyV7FlywayRepairIntegrationTest extends AbstractIntegrationTest {

    private static final String SCHEMA = "flyway_legacy_v7_test";

    @Autowired
    DataSource dataSource;

    @Test
    void repairsRealLegacyHistoryAndAppliesPendingMigrationBeforeStrictValidation() throws Exception {
        recreateSchema();
        try {
            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(SCHEMA)
                    .defaultSchema(SCHEMA)
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("17"))
                    .load()
                    .migrate();
            insertLegacyV7HistoryRow();

            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(SCHEMA)
                    .defaultSchema(SCHEMA)
                    .locations("classpath:db/migration")
                    .load();

            assertThat(migrationState(flyway, "7")).isEqualTo(MigrationState.MISSING_SUCCESS);
            assertThat(migrationState(flyway, "18")).isEqualTo(MigrationState.PENDING);

            LegacyV7FlywayRepairService service = new LegacyV7FlywayRepairService(
                    new LegacyV7FlywayRepairProperties(true));
            service.migrate(flyway);

            assertThat(migrationState(flyway, "7")).isEqualTo(MigrationState.DELETED);
            assertThat(migrationState(flyway, "18")).isEqualTo(MigrationState.SUCCESS);
            assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        } finally {
            dropSchema();
        }
    }

    private MigrationState migrationState(Flyway flyway, String version) {
        return Arrays.stream(flyway.info().all())
                .filter(info -> info.getVersion() != null && version.equals(info.getVersion().toString()))
                .map(info -> info.getState())
                .findFirst()
                .orElseThrow();
    }

    private void recreateSchema() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            statement.execute("CREATE SCHEMA " + SCHEMA);
        }
    }

    private void insertLegacyV7HistoryRow() throws Exception {
        String history = SCHEMA + ".flyway_schema_history";
        String sql = """
                INSERT INTO %s
                    (installed_rank, version, description, type, script, checksum,
                     installed_by, installed_on, execution_time, success)
                SELECT COALESCE(MAX(installed_rank), 0) + 1,
                       '7', 'dca take profit replaces timing', 'SQL',
                       'V7__dca_take_profit_replaces_timing.sql', 123456,
                       CURRENT_USER, CURRENT_TIMESTAMP, 0, TRUE
                FROM %s
                """.formatted(history, history);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private void dropSchema() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }
}
