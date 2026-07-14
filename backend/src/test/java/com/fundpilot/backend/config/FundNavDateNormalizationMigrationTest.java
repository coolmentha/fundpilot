package com.fundpilot.backend.config;

import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FundNavDateNormalizationMigrationTest extends AbstractIntegrationTest {

    private static final String SCHEMA = "flyway_fund_nav_date_test";

    @Autowired
    DataSource dataSource;

    @Test
    void normalizesBeijingBusinessDatesAndSoftDeletesDuplicates() throws Exception {
        recreateSchema();
        try {
            migrateToV20();
            insertLegacyNavRows();

            Flyway flyway = flyway().load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);

            assertThat(activeNav(1L)).isEqualTo(new NavRow(
                    Instant.parse("2026-07-13T00:00:00Z"),
                    new BigDecimal("1.04070000"),
                    new BigDecimal("1.04070000")));
            assertThat(activeNav(2L).navDate())
                    .isEqualTo(Instant.parse("2026-07-14T00:00:00Z"));
            assertThat(countRows(1L, false)).isEqualTo(1);
            assertThat(countRows(1L, true)).isEqualTo(2);
            assertThat(flyway.validateWithResult().validationSuccessful).isTrue();

            assertThatThrownBy(this::insertDuplicateActiveNav)
                    .isInstanceOf(SQLException.class)
                    .extracting(ex -> ((SQLException) ex).getSQLState())
                    .isEqualTo("23505");
        } finally {
            dropSchema();
        }
    }

    private void migrateToV20() {
        flyway()
                .target(MigrationVersion.fromVersion("20"))
                .load()
                .migrate();
    }

    private org.flywaydb.core.api.configuration.FluentConfiguration flyway() {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .locations("classpath:db/migration");
    }

    private void insertLegacyNavRows() throws SQLException {
        String sql = """
                INSERT INTO %1$s.fund
                    (id, version, created_date, updated_date, fund_code, fund_name, status)
                VALUES
                    (1, 0, now(), now(), '017175', '天弘国证绿色电力指数发起C', 'HOLDING'),
                    (2, 0, now(), now(), '000001', '测试基金', 'PENDING_HOLDING');

                INSERT INTO %1$s.fund_nav_history
                    (id, version, created_date, updated_date, fund_id, nav_date, nav, accumulated_nav)
                VALUES
                    (1, 0, '2026-07-13T22:00:00Z', '2026-07-13T22:00:00Z',
                     1, '2026-07-12T16:00:00Z', 1.0407, 1.0407),
                    (2, 0, '2026-07-13T20:00:00Z', '2026-07-13T20:00:00Z',
                     1, '2026-07-13T00:00:00Z', NULL, 9.9999),
                    (3, 0, '2026-07-14T20:00:00Z', '2026-07-14T20:00:00Z',
                     2, '2026-07-13T16:00:00Z', 1.1000, 1.1000);
                """.formatted(SCHEMA);
        execute(sql);
    }

    private NavRow activeNav(Long fundId) throws SQLException {
        String sql = """
                SELECT nav_date, nav, accumulated_nav
                FROM %s.fund_nav_history
                WHERE fund_id = %d AND deleted_date IS NULL
                """.formatted(SCHEMA, fundId);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            NavRow row = new NavRow(
                    result.getObject("nav_date", java.time.OffsetDateTime.class).toInstant(),
                    result.getBigDecimal("nav"),
                    result.getBigDecimal("accumulated_nav"));
            assertThat(result.next()).isFalse();
            return row;
        }
    }

    private int countRows(Long fundId, boolean includeDeleted) throws SQLException {
        String deletedClause = includeDeleted ? "" : " AND deleted_date IS NULL";
        String sql = "SELECT count(*) FROM %s.fund_nav_history WHERE fund_id = %d%s"
                .formatted(SCHEMA, fundId, deletedClause);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    private void insertDuplicateActiveNav() throws SQLException {
        execute("""
                INSERT INTO %s.fund_nav_history
                    (version, created_date, updated_date, fund_id, nav_date, nav, accumulated_nav)
                VALUES (0, now(), now(), 1, '2026-07-13T12:00:00Z', 1.2, 1.2)
                """.formatted(SCHEMA));
    }

    private void recreateSchema() throws SQLException {
        execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        execute("CREATE SCHEMA " + SCHEMA);
    }

    private void dropSchema() throws SQLException {
        execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private record NavRow(Instant navDate, BigDecimal nav, BigDecimal accumulatedNav) {
    }
}
