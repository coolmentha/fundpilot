package com.fundpilot.backend.config;

import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FundGroupOwnershipMigrationTest extends AbstractIntegrationTest {
    private static final String SCHEMA = "flyway_fund_group_owner_test";

    @Autowired
    DataSource dataSource;

    @Test
    void scopesActiveGroupNameUniquenessToOwner() throws Exception {
        recreateSchema();
        try {
            migrateToV37();
            insertUsersAndGroup();

            Flyway flyway = flyway().target(MigrationVersion.fromVersion("38")).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);

            execute("""
                    INSERT INTO %1$s.fund_group
                        (version, created_date, updated_date, owner_id, name, sort_order)
                    VALUES (0, now(), now(), 2, '核心', 0)
                    """.formatted(SCHEMA));
            assertThatThrownBy(() -> execute("""
                    INSERT INTO %1$s.fund_group
                        (version, created_date, updated_date, owner_id, name, sort_order)
                    VALUES (0, now(), now(), 1, '核心', 1)
                    """.formatted(SCHEMA)))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uq_fund_group_owner_name");
            assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        } finally {
            dropSchema();
        }
    }

    @Test
    void blocksActiveGroupWithoutOwnerBeforeChangingIndex() throws Exception {
        recreateSchema();
        try {
            migrateToV37();
            execute("""
                    INSERT INTO %1$s.fund_group
                        (version, created_date, updated_date, owner_id, name, sort_order)
                    VALUES (0, now(), now(), NULL, '待认领', 0)
                    """.formatted(SCHEMA));

            assertThatThrownBy(() -> flyway()
                    .target(MigrationVersion.fromVersion("38")).load().migrate())
                    .isInstanceOf(FlywayException.class)
                    .hasMessageContaining("fund_group owner-scoped uniqueness blocked");
        } finally {
            dropSchema();
        }
    }

    private void migrateToV37() {
        flyway().target(MigrationVersion.fromVersion("37")).load().migrate();
    }

    private void insertUsersAndGroup() throws SQLException {
        execute("""
                INSERT INTO %1$s.site_user
                    (id, version, created_date, updated_date, username, password_hash, role, enabled)
                VALUES
                    (1, 0, now(), now(), 'group-owner-1', 'hash', 'USER', true),
                    (2, 0, now(), now(), 'group-owner-2', 'hash', 'USER', true);
                INSERT INTO %1$s.fund_group
                    (version, created_date, updated_date, owner_id, name, sort_order)
                VALUES (0, now(), now(), 1, '核心', 0);
                """.formatted(SCHEMA));
    }

    private org.flywaydb.core.api.configuration.FluentConfiguration flyway() {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .locations("classpath:db/migration");
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
