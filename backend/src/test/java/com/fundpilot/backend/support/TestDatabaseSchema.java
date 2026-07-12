package com.fundpilot.backend.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** 测试 JVM 启动时只重置固定测试 schema，避免夹具污染开发业务数据。 */
final class TestDatabaseSchema {

    private static final String SCHEMA = "fundpilot_test";
    private static boolean reset;

    private TestDatabaseSchema() {
    }

    static synchronized void resetOnce() {
        if (reset) {
            return;
        }
        String url = System.getenv().getOrDefault(
                "TEST_DB_URL", "jdbc:postgresql://localhost:5432/fundpilot?currentSchema=" + SCHEMA);
        String username = System.getenv().getOrDefault("TEST_DB_USERNAME", "fundpilot");
        String password = System.getenv().getOrDefault("TEST_DB_PASSWORD", "fundpilot");
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            statement.execute("CREATE SCHEMA " + SCHEMA);
            reset = true;
        } catch (SQLException ex) {
            throw new IllegalStateException("无法初始化隔离测试 schema " + SCHEMA, ex);
        }
    }
}
