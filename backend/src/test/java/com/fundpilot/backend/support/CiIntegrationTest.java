package com.fundpilot.backend.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * CI 集成测试基类——启动共享 PostgreSQL 容器替代本地 PG。
 * <p>
 * CI 环境用 {@code -Dspring.profiles.active=ci} 激活本配置；本地开发用 {@link AbstractIntegrationTest} 直连本地 PG。
 * <p>
 * Testcontainers 通过 {@code @ServiceConnection} 自动注入数据源连接，覆盖 application.yml 默认的 localhost:5432。
 */
@Testcontainers
@ActiveProfiles("ci")
public abstract class CiIntegrationTest extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");
}
