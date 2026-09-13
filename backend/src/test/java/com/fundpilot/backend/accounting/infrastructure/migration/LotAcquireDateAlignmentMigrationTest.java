package com.fundpilot.backend.accounting.infrastructure.migration;

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

class LotAcquireDateAlignmentMigrationTest extends AbstractIntegrationTest {
    private static final String SCHEMA = "flyway_lot_acquire_date_test";
    @Autowired DataSource dataSource;

    @Test
    void realignsBackfilledLotsToTransactionTradeDate() throws Exception {
        execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        execute("CREATE SCHEMA " + SCHEMA);
        try {
            flyway().target(MigrationVersion.fromVersion("52")).load().migrate();

            // V14 回填风格的历史批次:acquire_date = confirm_time,与交易的 trade_date 不同
            execute("INSERT INTO " + SCHEMA + ".fund (id, fund_code) VALUES (1, '110022')");
            execute("INSERT INTO " + SCHEMA + ".fund_transaction (id, fund_id, status, source, "
                    + "confirm_time, trade_date, created_date) VALUES (11, 1, 'CONFIRMED', 'INCREASE', "
                    + "'2026-07-05T03:00:00+08:00', '2026-06-10T15:00:00+08:00', '2026-07-01T10:00:00+08:00')");
            execute("INSERT INTO " + SCHEMA + ".fund_lot (fund_id, acquire_tx_id, acquire_date, "
                    + "acquire_shares, remaining_shares, acquire_cost_per_share) "
                    + "SELECT 1, 11, confirm_time, 100, 100, 1.5 FROM " + SCHEMA
                    + ".fund_transaction WHERE id = 11");
            // 已对齐的批次(新确认路径):acquire_date = trade_date,迁移不应改动
            execute("INSERT INTO " + SCHEMA + ".fund_transaction (id, fund_id, status, source, "
                    + "confirm_time, trade_date, created_date) VALUES (12, 1, 'CONFIRMED', 'INCREASE', "
                    + "'2026-09-01T15:00:00+08:00', '2026-09-01T15:00:00+08:00', '2026-09-01T10:00:00+08:00')");
            execute("INSERT INTO " + SCHEMA + ".fund_lot (fund_id, acquire_tx_id, acquire_date, "
                    + "acquire_shares, remaining_shares, acquire_cost_per_share) "
                    + "SELECT 1, 12, trade_date, 50, 50, 1.4 FROM " + SCHEMA
                    + ".fund_transaction WHERE id = 12");

            Flyway flyway = flyway().target(MigrationVersion.fromVersion("53")).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);

            assertThat(timestamp("SELECT acquire_date FROM " + SCHEMA
                    + ".fund_lot WHERE acquire_tx_id = 11"))
                    .isEqualTo("2026-06-10T07:00:00Z");
            assertThat(timestamp("SELECT acquire_date FROM " + SCHEMA
                    + ".fund_lot WHERE acquire_tx_id = 12"))
                    .isEqualTo("2026-09-01T07:00:00Z");
            assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        } finally {
            execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    private org.flywaydb.core.api.configuration.FluentConfiguration flyway() {
        return Flyway.configure().dataSource(dataSource).schemas(SCHEMA).defaultSchema(SCHEMA)
                .locations("classpath:db/migration");
    }

    private String timestamp(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getObject(1, java.time.OffsetDateTime.class)
                    .toInstant().toString();
        }
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
