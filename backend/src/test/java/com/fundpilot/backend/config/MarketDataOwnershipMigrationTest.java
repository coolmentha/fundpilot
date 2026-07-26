package com.fundpilot.backend.config;

import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketDataOwnershipMigrationTest extends AbstractIntegrationTest {
    private static final String SCHEMA = "flyway_market_data_ownership_test";

    @Autowired
    DataSource dataSource;

    @Test
    void assignsProductsAndSplitsWatchedIndicesByOwner() throws Exception {
        recreateSchema();
        try {
            migrateToV38();
            insertLegacyRows();

            Flyway flyway = flyway().target(MigrationVersion.fromVersion("39")).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);

            assertThat(longValue("SELECT fund_product_id FROM " + SCHEMA
                    + ".fund_nav_history WHERE id = 101")).isEqualTo(11L);
            assertThat(longValue("SELECT fund_product_id FROM " + SCHEMA
                    + ".market_indicator_snapshot WHERE id = 201")).isEqualTo(11L);
            assertThat(longValue("SELECT count(*) FROM " + SCHEMA
                    + ".market_watched_index")).isEqualTo(2L);
            execute("""
                    INSERT INTO %1$s.market_watched_index
                        (owner_id, index_code, display_order)
                    VALUES (2, '1.000300', 0);
                    """.formatted(SCHEMA));
            assertThat(longValue("SELECT count(*) FROM " + SCHEMA
                    + ".market_watched_index WHERE index_code = '1.000300'"))
                    .isEqualTo(2L);
            assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        } finally {
            dropSchema();
        }
    }

    @Test
    void rejectsActiveMarketRowsWithoutProduct() throws Exception {
        recreateSchema();
        try {
            migrateToV38();
            execute("""
                    INSERT INTO %1$s.fund_nav_history
                        (id, version, created_date, updated_date, fund_code, nav_date, nav,
                         accumulated_nav, first_seen_at)
                    VALUES
                        (101, 0, now(), now(), 'MISSING', '2026-07-25T00:00:00Z', 1.1,
                         1.1, now());
                    """.formatted(SCHEMA));

            assertThatThrownBy(() -> flyway().target(MigrationVersion.fromVersion("39"))
                    .load().migrate())
                    .isInstanceOf(FlywayException.class)
                    .hasMessageContaining("active fund_nav_history rows remain without fund_product_id");
        } finally {
            dropSchema();
        }
    }

    private void insertLegacyRows() throws SQLException {
        execute("""
                INSERT INTO %1$s.site_user
                    (id, version, created_date, updated_date, username, password_hash, role, enabled)
                VALUES
                    (1, 0, now(), now(), 'market-owner-1', 'hash', 'USER', true),
                    (2, 0, now(), now(), 'market-owner-2', 'hash', 'USER', true);

                INSERT INTO %1$s.fund_product
                    (id, version, created_date, updated_date, fund_code, fund_name)
                VALUES (11, 0, now(), now(), '000001', '测试基金');

                INSERT INTO %1$s.fund
                    (id, version, created_date, updated_date, owner_id, product_id,
                     fund_code, fund_name, status)
                VALUES (21, 0, now(), now(), 1, 11, '000001', '测试基金', 'HOLDING');

                INSERT INTO %1$s.fund_nav_history
                    (id, version, created_date, updated_date, fund_id, fund_code, nav_date,
                     nav, accumulated_nav, first_seen_at)
                VALUES
                    (101, 0, now(), now(), 21, '000001', '2026-07-25T00:00:00Z',
                     1.1, 1.2, now());

                INSERT INTO %1$s.market_indicator_snapshot
                    (id, version, created_date, updated_date, fund_id, fund_code,
                     snapshot_date, current_nav)
                VALUES (201, 0, now(), now(), 21, '000001', '2026-07-25', 1.1);

                INSERT INTO %1$s.user_config
                    (id, version, created_date, updated_date, owner_id, watched_indices)
                VALUES
                    (301, 0, now(), now(), 1, '1.000001,1.000300,1.000300');
                """.formatted(SCHEMA));
    }

    private void migrateToV38() {
        flyway().target(MigrationVersion.fromVersion("38")).load().migrate();
    }

    private org.flywaydb.core.api.configuration.FluentConfiguration flyway() {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .locations("classpath:db/migration");
    }

    private long longValue(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
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
}
