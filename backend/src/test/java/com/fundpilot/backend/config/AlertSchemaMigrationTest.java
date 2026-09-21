package com.fundpilot.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fundpilot.backend.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 提醒规则与提醒记录两张表的迁移契约：表结构、内容约束与"同规则同交易日仅一条成功记录"的幂等索引。
 */
class AlertSchemaMigrationTest extends AbstractIntegrationTest {
    private static final String SCHEMA = "flyway_alert_test";

    @Autowired DataSource dataSource;

    @Test
    void createsAlertTablesWithConstraintsAndIdempotentSendIndex() throws Exception {
        recreateSchema();
        try {
            Flyway flyway = flyway().load();
            assertThat(flyway.migrate().success).isTrue();
            assertThat(flyway.validateWithResult().validationSuccessful).isTrue();

            assertThat(count("alert_rule")).isZero();
            assertThat(count("alert_notification")).isZero();
            insertOwnerAndPortfolioFund();

            long globalRule = insertRule("GLOBAL", null, new java.math.BigDecimal("0.05"));
            insertRule("FUND", 21L, new java.math.BigDecimal("0.10"));

            assertThatThrownBy(() -> execute("""
                    INSERT INTO %1$s.alert_rule (owner_id, scope, portfolio_fund_id, rule_type, threshold, enabled)
                    VALUES (1, 'GLOBAL', 21, 'RISE', 0.05, true);
                    """.formatted(SCHEMA)))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("ck_alert_rule_scope_target");
            assertThatThrownBy(() -> execute("""
                    INSERT INTO %1$s.alert_rule (owner_id, scope, portfolio_fund_id, rule_type, threshold, enabled)
                    VALUES (1, 'FUND', NULL, 'RISE', 0.05, true);
                    """.formatted(SCHEMA)))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("ck_alert_rule_scope_target");
            assertThatThrownBy(() -> insertRule("GLOBAL", null, java.math.BigDecimal.ZERO))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("ck_alert_rule_threshold");
            assertThatThrownBy(() -> insertRule("GLOBAL", null, new java.math.BigDecimal("1.5")))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("ck_alert_rule_threshold");
            assertThat(insertRule("GLOBAL", null, java.math.BigDecimal.ONE)).isPositive();

            long sent = insertNotification(globalRule, "2026-09-21T06:30:00Z", "SENT");
            assertThat(sent).isPositive();

            // 同一规则同一交易日再写一条成功记录会撞上部分唯一索引。
            assertThatThrownBy(() -> insertNotification(globalRule, "2026-09-21T07:30:00Z", "SENT"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uq_alert_notification_rule_day_sent");
            // 失败记录不占用当日额度,可重复写入以便重试。
            assertThat(insertNotification(globalRule, "2026-09-21T07:30:00Z", "FAILED")).isPositive();
            assertThat(insertNotification(globalRule, "2026-09-21T08:30:00Z", "FAILED")).isPositive();
            // 换一个交易日则允许再次成功发送。
            assertThat(insertNotification(globalRule, "2026-09-22T06:30:00Z", "SENT")).isPositive();

            assertThat(count("alert_rule")).isEqualTo(3);
            assertThat(count("alert_notification")).isEqualTo(4);
        } finally {
            execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    private void insertOwnerAndPortfolioFund() throws SQLException {
        execute("""
                INSERT INTO %1$s.site_user
                    (id, version, created_date, updated_date, username, password_hash, role, enabled)
                VALUES (1, 0, now(), now(), 'alert-owner', 'hash', 'USER', true);
                INSERT INTO %1$s.fund_product
                    (id, version, created_date, updated_date, fund_code, fund_name)
                VALUES (11, 0, now(), now(), '161725', '招商中证白酒');
                INSERT INTO %1$s.portfolio_fund
                    (id, owner_id, fund_product_id, validity, position_warning_enabled, position_warning_ratio)
                VALUES (21, 1, 11, 'TRACKED', true, 0.30);
                """.formatted(SCHEMA));
    }

    private long insertRule(String scope, Long portfolioFundId, java.math.BigDecimal threshold)
            throws SQLException {
        String sql = """
                INSERT INTO %s.alert_rule
                    (owner_id, scope, portfolio_fund_id, rule_type, threshold, enabled)
                VALUES (1, '%s', %s, 'RISE', %s, true)
                RETURNING id
                """.formatted(SCHEMA, scope, portfolioFundId == null ? "NULL" : portfolioFundId, threshold);
        return queryLong(sql);
    }

    private long insertNotification(long ruleId, String tradingDate, String status) throws SQLException {
        String sql = """
                INSERT INTO %s.alert_notification
                    (owner_id, alert_rule_id, trigger_type, threshold, trading_date, status,
                     fund_count, trigger_summary)
                VALUES (1, %d, 'RISE', 0.05, '%s', '%s', 1, '招商中证白酒(161725) 上涨5.32%%')
                RETURNING id
                """.formatted(SCHEMA, ruleId, tradingDate, status);
        return queryLong(sql);
    }

    private long queryLong(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private int count(String table) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT count(*) FROM " + SCHEMA + "." + table)) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    private void recreateSchema() throws SQLException {
        execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        execute("CREATE SCHEMA " + SCHEMA);
    }

    private org.flywaydb.core.api.configuration.FluentConfiguration flyway() {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .locations("classpath:db/migration");
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
