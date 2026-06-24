package com.fundpilot.backend.user.entity;

import com.fundpilot.backend.support.AbstractIntegrationTest;
import com.fundpilot.backend.user.repository.UserConfigRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #3 验收证据:user_config 表 + UserConfigEntity + UserConfigRepository 的 schema 对齐。
 * <p>本期单用户场景一行配置,存 {@code totalInvestableCapital}(用户账户总可投资金,
 * 总仓位 80% 硬约束的分母,见 CONTEXT.md「总可投资金」)。不存 userId。
 */
class UserConfigSchemaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EntityManager entityManager;

    @Autowired
    UserConfigRepository userConfigRepository;

    @Test
    @Transactional
    void userConfigPersistsTotalInvestableCapital() {
        UserConfigEntity config = new UserConfigEntity();
        config.setTotalInvestableCapital(new BigDecimal("1000000"));

        UserConfigEntity saved = userConfigRepository.save(config);
        entityManager.flush();
        entityManager.clear();

        UserConfigEntity reloaded = userConfigRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getTotalInvestableCapital())
                .isEqualByComparingTo(new BigDecimal("1000000"));
    }
}
