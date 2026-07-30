package com.fundpilot.backend.support;

import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi;
import com.fundpilot.backend.identityaccess.application.command.currentactor.CurrentActorCommandHandler;
import com.fundpilot.backend.identityaccess.application.gateway.currentactor.ActorContext;
import com.fundpilot.backend.identityaccess.application.query.currentactor.CurrentActor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 集成测试基类——使用本地 PostgreSQL 的独立 {@code fundpilot_test} schema，
 * Flyway 真实跑迁移,Hibernate {@code validate} 真实校验 JPA 字段映射与表列一致。
 * <p>
 * 用 {@link SpringBootTest} 而非 {@code @DataJpaTest}:{@code @DataJpaTest} 切片默认排除
 * FlywayAutoConfiguration,真 PG 下表不会自动建,Hibernate validate 报 missing table。
 * {@code @SpringBootTest} 保留完整 autoconfig,Flyway 在容器初始化时自动跑迁移,
 * 先于 EntityManagerFactory 构建,时序正确。
 * <p>
 * 每个测试 JVM 在 Spring 上下文创建前重置测试 schema，绝不触碰业务 {@code public} schema。
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {
    static {
        TestDatabaseSchema.resetOnce();
    }

    @Autowired
    private UserAdministrationApi users;

    @Autowired
    private CurrentActorCommandHandler currentActorCommands;

    private ActorContext.Scope actorScope;
    private long testActorId;

    @BeforeEach
    void bindTestActor() {
        testActorId = users.ensureBootstrapAdmin("integration-test-admin", "integration-test-password").id();
        actorScope = currentActorCommands.open(CurrentActor.system(testActorId));
    }

    @AfterEach
    void clearTestActor() {
        if (actorScope != null) {
            actorScope.close();
            actorScope = null;
        }
    }

    protected final long testActorId() {
        return testActorId;
    }
}
