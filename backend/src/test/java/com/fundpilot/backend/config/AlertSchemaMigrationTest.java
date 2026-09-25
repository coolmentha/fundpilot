package com.fundpilot.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fundpilot.backend.alerting.application.condition.AlertConditionJsonCodec;
import com.fundpilot.backend.alerting.domain.condition.AlertConditionMatch;
import com.fundpilot.backend.alerting.domain.condition.ConditionRelation;
import com.fundpilot.backend.alerting.domain.condition.IndicatorCode;
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

            long globalRule = insertRule("GLOBAL", null, dailyChangeAbove("0.05"));
            insertRule("FUND", 21L, dailyChangeAbove("0.10"));

            assertThatThrownBy(() -> execute("""
                    INSERT INTO %1$s.alert_rule (owner_id, scope, portfolio_fund_id, conditions, enabled)
                    VALUES (1, 'GLOBAL', 21, '%2$s', true);
                    """.formatted(SCHEMA, dailyChangeAbove("0.05"))))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("ck_alert_rule_scope_target");
            assertThatThrownBy(() -> execute("""
                    INSERT INTO %1$s.alert_rule (owner_id, scope, portfolio_fund_id, conditions, enabled)
                    VALUES (1, 'FUND', NULL, '%2$s', true);
                    """.formatted(SCHEMA, dailyChangeAbove("0.05"))))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("ck_alert_rule_scope_target");
            // 条件数组是唯一触发口径，必填。
            assertThatThrownBy(() -> execute("""
                    INSERT INTO %1$s.alert_rule (owner_id, scope, portfolio_fund_id, enabled)
                    VALUES (1, 'GLOBAL', NULL, true);
                    """.formatted(SCHEMA)))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("conditions");

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

            assertThat(count("alert_rule")).isEqualTo(2);
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

    /** 存量三阈值规则迁移后仍表达同一口径：上涨→涨跌幅高于阈值，下跌→涨跌幅低于负阈值。 */
    @Test
    void backfillsLegacyThreeThresholdRulesIntoEquivalentConditions() throws Exception {
        recreateSchema();
        try {
            migrateTo("57");
            insertOwnerAndPortfolioFund();
            execute("""
                    INSERT INTO %1$s.alert_rule (owner_id, scope, portfolio_fund_id, rule_type, threshold, enabled)
                    VALUES (1, 'GLOBAL', NULL, 'RISE', 0.05, true),
                           (1, 'GLOBAL', NULL, 'DROP', 0.03, true),
                           (1, 'GLOBAL', NULL, 'PROFIT', 0.15, true);
                    """.formatted(SCHEMA));

            migrateTo(null);

            assertThat(conditionThreshold("RISE")).isEqualByComparingTo("0.05");
            assertThat(relationOf("RISE")).isEqualTo(ConditionRelation.ABOVE);
            assertThat(conditionThreshold("DROP")).isEqualByComparingTo("-0.03");
            assertThat(relationOf("DROP")).isEqualTo(ConditionRelation.BELOW);
            assertThat(indicatorOf("PROFIT")).isEqualTo(IndicatorCode.HOLDING_RETURN);
            assertThat(conditionThreshold("PROFIT")).isEqualByComparingTo("0.15");
        } finally {
            execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    private ConditionRelation relationOf(String ruleType) throws SQLException {
        return condition(ruleType).relation();
    }

    private IndicatorCode indicatorOf(String ruleType) throws SQLException {
        return condition(ruleType).indicator();
    }

    private java.math.BigDecimal conditionThreshold(String ruleType) throws SQLException {
        return condition(ruleType).effectiveThreshold();
    }

    private com.fundpilot.backend.alerting.domain.condition.AlertCondition condition(String ruleType)
            throws SQLException {
        String conditions = queryString(
                "SELECT conditions FROM " + SCHEMA + ".alert_rule WHERE rule_type = '" + ruleType + "'");
        var group = AlertConditionJsonCodec.read(conditions);
        assertThat(group.match()).isEqualTo(AlertConditionMatch.ALL);
        return group.conditions().getFirst();
    }

    private long insertRule(String scope, Long portfolioFundId, String conditions) throws SQLException {
        String sql = """
                INSERT INTO %s.alert_rule
                    (owner_id, scope, portfolio_fund_id, conditions, enabled)
                VALUES (1, '%s', %s, '%s', true)
                RETURNING id
                """.formatted(SCHEMA, scope, portfolioFundId == null ? "NULL" : portfolioFundId, conditions);
        return queryLong(sql);
    }

    /** 新口径的提醒记录只带条件快照，存量触发行情列一律为空。 */
    private long insertNotification(long ruleId, String tradingDate, String status) throws SQLException {
        String sql = """
                INSERT INTO %s.alert_notification
                    (owner_id, alert_rule_id, conditions_snapshot, trading_date, status,
                     fund_count, trigger_summary)
                VALUES (1, %d, '%s', '%s', '%s', 1, '招商中证白酒(161725) 命中：当日涨跌幅 高于 0.05，现值 0.06')
                RETURNING id
                """.formatted(SCHEMA, ruleId, dailyChangeAbove("0.05"), tradingDate, status);
        return queryLong(sql);
    }

    private static String dailyChangeAbove(String threshold) {
        return """
                {"match":"ALL","conditions":[{"indicator":"DAILY_CHANGE","params":{},"relation":"ABOVE",\
                "value":%s}]}""".formatted(threshold);
    }

    private long queryLong(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private String queryString(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    /** 迁移到指定版本，version 为 null 表示最新版本。 */
    private void migrateTo(String version) {
        var configuration = flyway();
        if (version != null) {
            configuration = configuration.target(version);
        }
        assertThat(configuration.load().migrate().success).isTrue();
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
