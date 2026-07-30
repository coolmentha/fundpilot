package com.fundpilot.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundpilot.backend.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AccountingOwnershipMigrationTest extends AbstractIntegrationTest {
    private static final String SCHEMA = "flyway_accounting_ownership_test";

    @Autowired DataSource dataSource;

    @Test
    void backfillsLedgerLotsAndPositionWithoutChangingLegacyFacts() throws Exception {
        execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        execute("CREATE SCHEMA " + SCHEMA);
        try {
            flyway().target(MigrationVersion.fromVersion("39")).load().migrate();
            insertLegacyFacts();

            Flyway flyway = flyway().target(MigrationVersion.fromVersion("40")).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);

            assertThat(value("SELECT portfolio_fund_id FROM " + SCHEMA
                    + ".fund_transaction WHERE id = 31")).isEqualTo("21");
            assertThat(value("SELECT portfolio_fund_id FROM " + SCHEMA
                    + ".fund_lot WHERE id = 41")).isEqualTo("21");
            assertThat(row("SELECT owner_id, status, cost_per_share FROM " + SCHEMA
                    + ".accounting_position WHERE portfolio_fund_id = 21"))
                    .containsExactly("7", "OPEN", "1.25000000");
            assertThat(instantValue("SELECT opened_at FROM " + SCHEMA
                    + ".accounting_position WHERE portfolio_fund_id = 21"))
                    .isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
            assertThat(value("SELECT fund_id FROM " + SCHEMA
                    + ".fund_transaction WHERE id = 31")).isEqualTo("11");
            assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        } finally {
            execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    private void insertLegacyFacts() throws SQLException {
        execute("""
                INSERT INTO %1$s.site_user
                    (id, version, created_date, updated_date, username, password_hash, role, enabled)
                VALUES (7, 0, now(), now(), 'accounting-owner', 'hash', 'USER', true);
                INSERT INTO %1$s.fund_product
                    (id, version, created_date, updated_date, fund_code, fund_name)
                VALUES (17, 0, now(), now(), '000001', '迁移测试基金');
                INSERT INTO %1$s.fund
                    (id, version, created_date, updated_date, owner_id, product_id, fund_code, fund_name,
                     status, opened_at, cost_per_share)
                VALUES (11, 0, now(), now(), 7, 17, '000001', '迁移测试基金', 'HOLDING',
                        '2026-07-01T00:00:00Z', 1.25);
                INSERT INTO %1$s.portfolio_fund
                    (id, owner_id, fund_product_id, legacy_fund_id, validity,
                     position_warning_enabled, position_warning_ratio)
                VALUES (21, 7, 17, 11, 'TRACKED', true, 0.30);
                INSERT INTO %1$s.fund_transaction
                    (id, version, created_date, updated_date, fund_id, amount, status, source, shares,
                     nav, confirm_time)
                VALUES (31, 0, now(), now(), 11, 125, 'CONFIRMED', 'INVEST', 100, 1.25,
                        '2026-07-01T00:00:00Z');
                INSERT INTO %1$s.fund_lot
                    (id, version, fund_id, acquire_tx_id, acquire_date, acquire_shares,
                     remaining_shares, acquire_cost_per_share)
                VALUES (41, 0, 11, 31, '2026-07-01T00:00:00Z', 100, 100, 1.25);
                """.formatted(SCHEMA));
    }

    private org.flywaydb.core.api.configuration.FluentConfiguration flyway() {
        return Flyway.configure().dataSource(dataSource).schemas(SCHEMA).defaultSchema(SCHEMA)
                .locations("classpath:db/migration");
    }

    private String value(String sql) throws SQLException {
        return row(sql)[0];
    }

    private Instant instantValue(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            Instant value = result.getTimestamp(1).toInstant();
            assertThat(result.next()).isFalse();
            return value;
        }
    }

    private String[] row(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            String[] row = new String[result.getMetaData().getColumnCount()];
            for (int i = 0; i < row.length; i++) row[i] = result.getString(i + 1);
            assertThat(result.next()).isFalse();
            return row;
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
