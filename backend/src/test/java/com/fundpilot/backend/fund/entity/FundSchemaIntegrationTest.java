package com.fundpilot.backend.fund.entity;

import com.fundpilot.backend.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tracer bullet + schema 对齐断言:证明应用能在真实 PostgreSQL + Flyway 真实跑迁移 +
 * Hibernate validate 的完整链路下加载(循环 1),并断言 ADR-0001/0002 的字段变更确实生效(循环 3/4 的前置)。
 *
 * <p>任何 schema 不一致、迁移 SQL 错误、JPA 字段映射偏离都会让本测试红。
 * 这是 issue #2 验收准则"启动通过 Hibernate validate"的可执行证据。
 */
class FundSchemaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EntityManager entityManager;

    @Test
    void applicationLoadsWithRealPostgresAndFlywayMigrations() {
        // 上下文加载成功即通过——失败时 Spring 会抛异常使测试红。
        // 这条同时验证 Flyway V1+V2 迁移跑通 + Hibernate validate 通过。
    }

    @Test
    void fundEntityHasFundSubTypeAndBenchmarkIndexCodeColumns() {
        // ADR-0002:新增数据源维度字段
        Set<String> attributeNames = attributeNamesOf(FundEntity.class);
        assertThat(attributeNames).contains("fundSubType", "benchmarkIndexCode", "openedAt");
    }

    @Test
    void fundEntityHasNoPeakNavOrHoldingPeriodPeakNavColumns() {
        // ADR-0001:删除派生字段,改为实时派生
        Set<String> attributeNames = attributeNamesOf(FundEntity.class);
        assertThat(attributeNames).doesNotContain("peakNav", "holdingPeriodPeakNav");
    }

    @SuppressWarnings("unchecked")
    private Set<String> attributeNamesOf(Class<?> entityClass) {
        EntityType<?> entityType = entityManager.getMetamodel()
                .getEntities().stream()
                .filter(e -> e.getJavaType().equals(entityClass))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        entityClass.getSimpleName() + " not found in metamodel"));
        return (Set<String>) entityType.getAttributes().stream()
                .map(Attribute::getName)
                .collect(Collectors.toSet());
    }
}
