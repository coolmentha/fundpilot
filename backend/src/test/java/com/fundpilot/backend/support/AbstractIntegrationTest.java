package com.fundpilot.backend.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 集成测试基类——本地开发直连本地 PostgreSQL({@code application-test.yml}),
 * Flyway 真实跑 V1+V2 迁移,Hibernate {@code validate} 真实校验 JPA 字段映射与表列一致。
 * <p>
 * 用 {@link SpringBootTest} 而非 {@code @DataJpaTest}:{@code @DataJpaTest} 切片默认排除
 * FlywayAutoConfiguration,真 PG 下表不会自动建,Hibernate validate 报 missing table。
 * {@code @SpringBootTest} 保留完整 autoconfig,Flyway 在容器初始化时自动跑迁移,
 * 先于 EntityManagerFactory 构建,时序正确。
 * <p>
 * <b>本地 vs CI</b>:本地连 {@code localhost:5432/postgres},
 * CI 用 {@link CiIntegrationTest} 启动 Testcontainers PG 容器。
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    // CI 用 Testcontainers 时启用(CiIntegrationTest 继承本类并加 @Container),
    // 本地开发不带该注解,直接连 application-test.yml 的本地 PG。
}
