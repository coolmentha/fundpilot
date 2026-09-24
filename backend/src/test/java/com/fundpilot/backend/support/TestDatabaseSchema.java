package com.fundpilot.backend.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 测试 JVM 启动时只重置固定测试 schema，避免夹具污染开发业务数据。 */
public final class TestDatabaseSchema {

    private static final String SCHEMA = "fundpilot_test";
    private static final String DEFAULT_URL = "jdbc:postgresql://localhost:5432/fundpilot";
    private static final Set<String> RESET = ConcurrentHashMap.newKeySet();

    private TestDatabaseSchema() {
    }

    static synchronized void resetOnce() {
        resetOnce(SCHEMA);
    }

    /**
     * 供使用独立 schema 的集成测试在静态初始化时调用。这些 schema 不在默认重置范围内，若不重建，
     * 上一次运行留下的 {@code flyway_schema_history} 会在迁移脚本改名或改内容后与本地不一致，
     * 使 Flyway 校验失败、应用上下文整体加载失败。
     */
    public static synchronized void resetOnce(String schema) {
        if (!RESET.add(schema)) {
            return;
        }
        String username = System.getenv().getOrDefault("TEST_DB_USERNAME", "fundpilot");
        String password = System.getenv().getOrDefault("TEST_DB_PASSWORD", "fundpilot");
        try (Connection connection = DriverManager.getConnection(urlFor(schema), username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            statement.execute("CREATE SCHEMA " + schema);
        } catch (SQLException ex) {
            throw new IllegalStateException("无法初始化隔离测试 schema " + schema, ex);
        }
    }

    /** {@code TEST_DB_URL} 只描述库位置，schema 由调用方指定，避免覆盖后误删共享测试 schema。 */
    private static String urlFor(String schema) {
        String base = System.getenv().getOrDefault("TEST_DB_URL", DEFAULT_URL);
        int query = base.indexOf('?');
        return (query >= 0 ? base.substring(0, query) : base) + "?currentSchema=" + schema;
    }
}