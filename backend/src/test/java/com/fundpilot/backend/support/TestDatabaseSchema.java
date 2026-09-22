package com.fundpilot.backend.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 测试 JVM 启动时只重置本 fork 独占的测试 schema，避免夹具污染开发业务数据。
 * <p>
 * schema 名取自系统属性 {@code fundpilot.test.schema}——由 surefire 按 fork 注入（见 pom.xml），
 * 并行执行时每个 fork 拿到互不相同的 schema，各自的 {@code DROP SCHEMA CASCADE} 不会互相踩踏。
 * IDE 单跑或未开并行时该属性缺失，回退 {@code fundpilot_test}，与 {@code application-test.yml} 的占位符默认值一致。
 */
final class TestDatabaseSchema {

    private static final String DEFAULT_SCHEMA = "fundpilot_test";
    private static boolean reset;

    private TestDatabaseSchema() {
    }

    private static String schema() {
        return System.getProperty("fundpilot.test.schema", DEFAULT_SCHEMA);
    }

    static synchronized void resetOnce() {
        if (reset) {
            return;
        }
        String schema = schema();
        String url = System.getenv().getOrDefault(
                "TEST_DB_URL", "jdbc:postgresql://localhost:5432/fundpilot?currentSchema=" + schema);
        String username = System.getenv().getOrDefault("TEST_DB_USERNAME", "fundpilot");
        String password = System.getenv().getOrDefault("TEST_DB_PASSWORD", "fundpilot");
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            statement.execute("CREATE SCHEMA " + schema);
            reset = true;
        } catch (SQLException ex) {
            throw new IllegalStateException("无法初始化隔离测试 schema " + schema, ex);
        }
    }
}
