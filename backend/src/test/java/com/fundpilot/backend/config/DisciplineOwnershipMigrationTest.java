package com.fundpilot.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundpilot.backend.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DisciplineOwnershipMigrationTest extends AbstractIntegrationTest {
    private static final String SCHEMA = "flyway_discipline_ownership_test";

    @Autowired DataSource dataSource;

    @Test
    void backfillsStrategyAdviceAndResponseLinkWithoutChangingLegacyFacts() throws Exception {
        execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        execute("CREATE SCHEMA " + SCHEMA);
        try {
            flyway().target(MigrationVersion.fromVersion("40")).load().migrate();
            insertLegacyFacts();

            Flyway flyway = flyway().target(MigrationVersion.fromVersion("41")).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);

            assertThat(row("SELECT legacy_strategy_id, portfolio_fund_id, owner_id, status, "
                    + "take_profit_phase, cycle_peak_nav, preset_fund_category, customized FROM " + SCHEMA
                    + ".discipline_strategy WHERE legacy_strategy_id = 51"))
                    .containsExactly("51", "21", "7", "EFFECTIVE", "TRIGGERED", "1.50000000",
                            "SECTOR", "t");
            assertThat(row("SELECT advice.legacy_signal_id, advice.portfolio_fund_id, advice.owner_id, strategy.legacy_strategy_id, "
                    + "signal_type, trigger_tier, coefficient, suggested_value, suggested_measure_unit, reason, "
                    + "warnings, hard_constraint_breaches, response_status FROM " + SCHEMA
                    + ".discipline_advice advice JOIN " + SCHEMA
                    + ".discipline_strategy strategy ON strategy.id = advice.discipline_strategy_id "
                    + "WHERE advice.legacy_signal_id = 61"))
                    .containsExactly("61", "21", "7", "51", "3", "2", "0.75000000", "12.50000000",
                            "SHARES", "TRAILING_STOP", "TIER_CLEARED:1", "MIN_HOLD_DAYS", "PENDING");
            assertThat(value("SELECT advice.legacy_signal_id FROM " + SCHEMA + ".fund_transaction tx "
                    + "JOIN " + SCHEMA + ".discipline_advice advice "
                    + "ON advice.id = tx.discipline_advice_id WHERE tx.id = 71")).isEqualTo("61");
            assertThat(value("SELECT signal_log_id FROM " + SCHEMA
                    + ".fund_transaction WHERE id = 71")).isEqualTo("61");
            assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        } finally {
            execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    private void insertLegacyFacts() throws SQLException {
        execute("""
                INSERT INTO %1$s.site_user
                    (id, version, created_date, updated_date, username, password_hash, role, enabled)
                VALUES (7, 0, now(), now(), 'discipline-owner', 'hash', 'USER', true);
                INSERT INTO %1$s.fund_product
                    (id, version, created_date, updated_date, fund_code, fund_name)
                VALUES (17, 0, now(), now(), '000002', '纪律迁移测试基金');
                INSERT INTO %1$s.fund
                    (id, version, created_date, updated_date, owner_id, product_id, fund_code, fund_name,
                     fund_category, status)
                VALUES (11, 0, now(), now(), 7, 17, '000002', '纪律迁移测试基金', 'SECTOR', 'HOLDING');
                INSERT INTO %1$s.portfolio_fund
                    (id, owner_id, fund_product_id, legacy_fund_id, validity,
                     position_warning_enabled, position_warning_ratio)
                VALUES (21, 7, 17, 11, 'TRACKED', false, 0.30);
                INSERT INTO %1$s.fund_strategy
                    (id, version, created_date, updated_date, fund_id, status,
                     stop_loss_pullback_percent, profit_activation_percent, profit_harvest_percent,
                     minimum_holding_percent, max_single_sell_percent, cooldown_trading_days,
                     preset_fund_category, preset_version, customized, take_profit_phase,
                     cycle_started_at, cycle_peak_nav, triggered_signal_id, cooldown_started_at)
                VALUES (51, 2, now(), now(), 11, 'EFFECTIVE', 0.08, 0.20, 0.50, 0.40, 0.20, 10,
                        'SECTOR', 1, true, 'TRIGGERED', '2026-07-01T00:00:00Z', 1.50, 61, null);
                INSERT INTO %1$s.signal_log
                    (id, version, created_date, updated_date, fund_id, fund_strategy_id, signal_date,
                     trigger_nav, trigger_tier, coefficient, signal_type, value, measure_unit,
                     reason, warnings, hard_constraint_breaches)
                VALUES (61, 0, now(), now(), 11, 51, '2026-07-29T00:00:00Z', 1.40, 2, 0.75,
                        'SELL', 12.5, 'SHARES', 'TRAILING_STOP', 'TIER_CLEARED:1', 'MIN_HOLD_DAYS');
                INSERT INTO %1$s.fund_transaction
                    (id, version, created_date, updated_date, fund_id, amount, status, source, shares,
                     signal_log_id)
                VALUES (71, 0, now(), now(), 11, null, 'PENDING', 'DECREASE', 12.5, 61);
                """.formatted(SCHEMA));
    }

    private org.flywaydb.core.api.configuration.FluentConfiguration flyway() {
        return Flyway.configure().dataSource(dataSource).schemas(SCHEMA).defaultSchema(SCHEMA)
                .locations("classpath:db/migration");
    }

    private String value(String sql) throws SQLException {
        return row(sql)[0];
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
