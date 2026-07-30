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

class InvestmentPlanOwnershipMigrationTest extends AbstractIntegrationTest {
    private static final String SCHEMA = "flyway_investment_plan_ownership_test";

    @Autowired DataSource dataSource;

    @Test
    void backfillsPlansBudgetAndTransactionSourceWithoutChangingLegacyFacts() throws Exception {
        execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        execute("CREATE SCHEMA " + SCHEMA);
        try {
            flyway().target(MigrationVersion.fromVersion("41")).load().migrate();
            insertLegacyFacts();

            Flyway flyway = flyway().target(MigrationVersion.fromVersion("42")).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);

            assertThat(row("SELECT legacy_dca_plan_id, portfolio_fund_id, owner_id, enabled, amount, "
                    + "frequency, day_of_week, day_of_month, status FROM " + SCHEMA
                    + ".investment_plan WHERE legacy_dca_plan_id = 51"))
                    .containsExactly("51", "21", "7", "t", "100.00000000", "WEEKLY", "3", null,
                            "EFFECTIVE");
            assertThat(row("SELECT owner_id, monthly_budget FROM " + SCHEMA
                    + ".investment_plan_budget WHERE owner_id = 7"))
                    .containsExactly("7", "3000.00000000");
            assertThat(value("SELECT investment_plan_id FROM " + SCHEMA
                    + ".fund_transaction WHERE id = 71"))
                    .isEqualTo(value("SELECT id FROM " + SCHEMA
                            + ".investment_plan WHERE legacy_dca_plan_id = 51"));
            assertThat(value("SELECT dca_plan_id FROM " + SCHEMA
                    + ".fund_transaction WHERE id = 71")).isEqualTo("51");
            assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        } finally {
            execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    private void insertLegacyFacts() throws SQLException {
        execute("""
                INSERT INTO %1$s.site_user
                    (id, version, created_date, updated_date, username, password_hash, role, enabled)
                VALUES (7, 0, now(), now(), 'plan-owner', 'hash', 'USER', true);
                INSERT INTO %1$s.fund_product
                    (id, version, created_date, updated_date, fund_code, fund_name)
                VALUES (17, 0, now(), now(), '000003', '定投迁移测试基金');
                INSERT INTO %1$s.fund
                    (id, version, created_date, updated_date, owner_id, product_id, fund_code, fund_name, status)
                VALUES (11, 0, now(), now(), 7, 17, '000003', '定投迁移测试基金', 'WATCHING');
                INSERT INTO %1$s.portfolio_fund
                    (id, owner_id, fund_product_id, legacy_fund_id, validity,
                     position_warning_enabled, position_warning_ratio)
                VALUES (21, 7, 17, 11, 'TRACKED', false, 0.30);
                INSERT INTO %1$s.fund_dca_plan
                    (id, version, fund_id, enabled, amount, frequency, day_of_week, status,
                     created_date, updated_date)
                VALUES (51, 2, 11, true, 100, 'WEEKLY', 3, 'EFFECTIVE', now(), now());
                INSERT INTO %1$s.user_config
                    (id, version, created_date, updated_date, owner_id, monthly_dca_budget)
                VALUES (61, 0, now(), now(), 7, 3000);
                INSERT INTO %1$s.fund_transaction
                    (id, version, created_date, updated_date, fund_id, amount, status, source,
                     trade_date, dca_plan_id)
                VALUES (71, 0, now(), now(), 11, 100, 'PENDING', 'INVEST',
                        '2026-07-29T00:00:00Z', 51);
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
