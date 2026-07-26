package com.fundpilot.backend.config;

import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioFundMigrationTest extends AbstractIntegrationTest {
    private static final String SCHEMA = "flyway_portfolio_fund_test";

    @Autowired
    DataSource dataSource;

    @Test
    void backfillsTrackedAndVoidedPortfolioFundsWithGroupsAndAudit() throws Exception {
        recreateSchema();
        try {
            migrateToV36();
            insertLegacyPortfolioRows();

            Flyway flyway = flyway()
                    .target(MigrationVersion.fromVersion("37"))
                    .load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);

            assertThat(portfolioFund(101L)).isEqualTo(new PortfolioFundRow(
                    1L, 11L, "TRACKED", null, null, null));
            assertThat(portfolioFund(102L)).isEqualTo(new PortfolioFundRow(
                    1L, 12L, "VOIDED", Instant.parse("2026-07-20T09:30:00Z"),
                    1L, "LEGACY_ARCHIVED"));
            assertThat(count("portfolio_fund")).isEqualTo(2);
            assertThat(count("portfolio_fund_group_member")).isEqualTo(1);
            insertNewPortfolioFundWithoutLegacyRow();
            assertThat(count("portfolio_fund")).isEqualTo(3);
            assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        } finally {
            dropSchema();
        }
    }

    private void migrateToV36() {
        flyway()
                .target(MigrationVersion.fromVersion("36"))
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

    private void insertLegacyPortfolioRows() throws SQLException {
        execute("""
                INSERT INTO %1$s.site_user
                    (id, version, created_date, updated_date, username, password_hash, role, enabled)
                VALUES (1, 0, now(), now(), 'portfolio-owner', 'hash', 'ADMIN', true);

                INSERT INTO %1$s.fund_product
                    (id, version, created_date, updated_date, fund_code, fund_name)
                VALUES
                    (11, 0, now(), now(), '000001', '有效基金'),
                    (12, 0, now(), now(), '000002', '旧归档基金');

                INSERT INTO %1$s.fund
                    (id, version, created_date, updated_date, deleted_date,
                     owner_id, product_id, fund_code, fund_name, status,
                     position_warning_enabled, position_warning_ratio)
                VALUES
                    (101, 3, '2026-07-01T00:00:00Z', '2026-07-19T00:00:00Z', NULL,
                     1, 11, '000001', '有效基金', 'HOLDING', true, 0.30),
                    (102, 4, '2026-06-01T00:00:00Z', '2026-07-20T09:30:00Z',
                     '2026-07-20T09:30:00Z', 1, 12, '000002', '旧归档基金',
                     'PENDING_HOLDING', false, 0.25);

                INSERT INTO %1$s.fund_group
                    (id, version, created_date, updated_date, name, sort_order, owner_id)
                VALUES (21, 0, now(), now(), '核心', 0, 1);

                INSERT INTO %1$s.fund_group_member (fund_id, group_id)
                VALUES (101, 21);
                """.formatted(SCHEMA));
    }

    private PortfolioFundRow portfolioFund(long legacyFundId) throws SQLException {
        String sql = """
                SELECT owner_id, fund_product_id, validity, voided_at, voided_by, void_reason
                FROM %s.portfolio_fund
                WHERE legacy_fund_id = %d
                """.formatted(SCHEMA, legacyFundId);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            PortfolioFundRow row = new PortfolioFundRow(
                    result.getLong("owner_id"),
                    result.getLong("fund_product_id"),
                    result.getString("validity"),
                    result.getObject("voided_at", java.time.OffsetDateTime.class) == null
                            ? null
                            : result.getObject("voided_at", java.time.OffsetDateTime.class).toInstant(),
                    result.getObject("voided_by", Long.class),
                    result.getString("void_reason"));
            assertThat(result.next()).isFalse();
            return row;
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

    private void insertNewPortfolioFundWithoutLegacyRow() throws SQLException {
        execute("""
                INSERT INTO %1$s.fund_product
                    (id, version, created_date, updated_date, fund_code, fund_name)
                VALUES (13, 0, now(), now(), '000003', '新组合基金');
                INSERT INTO %1$s.portfolio_fund
                    (owner_id, fund_product_id, validity,
                     position_warning_enabled, position_warning_ratio)
                VALUES (1, 13, 'TRACKED', true, 0.30);
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

    private record PortfolioFundRow(long ownerId, long fundProductId, String validity,
                                    Instant voidedAt, Long voidedBy, String voidReason) {
    }
}
