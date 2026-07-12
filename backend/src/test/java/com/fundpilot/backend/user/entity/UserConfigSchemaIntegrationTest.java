package com.fundpilot.backend.user.entity;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import com.fundpilot.backend.user.repository.UserConfigRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * user_config 表 + UserConfigEntity schema 对齐验证。
 *
 * <p>UserConfig 保存 {@code watchedIndices} 与 V20 恢复的 {@code totalCapital} 风险预算。
 */
class UserConfigSchemaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EntityManager entityManager;

    @Autowired
    UserConfigRepository userConfigRepository;

    @Test
    @Transactional
    void userConfigPersistsWatchedIndices() {
        UserConfigEntity config = existingOrNewConfig();
        config.setWatchedIndices("1.000001,1.000300,0.399006");

        UserConfigEntity saved = userConfigRepository.save(config);
        entityManager.flush();
        entityManager.clear();

        UserConfigEntity reloaded = userConfigRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getWatchedIndices()).isEqualTo("1.000001,1.000300,0.399006");
    }

    @Test
    @Transactional
    void watchedIndicesNullable_未配置时允许为空() {
        UserConfigEntity config = existingOrNewConfig();
        config.setWatchedIndices(null);
        // watchedIndices 不设(null),用默认指数列表由服务层兜底

        UserConfigEntity saved = userConfigRepository.save(config);
        entityManager.flush();
        entityManager.clear();

        UserConfigEntity reloaded = userConfigRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getWatchedIndices()).isNull();
    }

    @Test
    @Transactional
    void activeUserConfig_数据库只允许一行() {
        UserConfigEntity first = existingOrNewConfig();
        first.setWatchedIndices("1.000001");
        userConfigRepository.saveAndFlush(first);

        UserConfigEntity second = new UserConfigEntity();
        second.setWatchedIndices("1.000300");

        assertThatThrownBy(() -> userConfigRepository.saveAndFlush(second))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void fundMaxPositionRatio_数据库拒绝超过30百分比() {
        FundEntity fund = new FundEntity();
        fund.setFundCode("510300");
        fund.setFundName("沪深300ETF");
        fund.setFundCategory(FundCategory.BROAD_BASE);
        fund.setMaxPositionRatio(new BigDecimal("0.31"));

        assertThatThrownBy(() -> {
            entityManager.persist(fund);
            entityManager.flush();
        }).isInstanceOf(org.hibernate.exception.ConstraintViolationException.class);
    }

    @Test
    @Transactional
    void totalCapital_数据库拒绝非正值() {
        UserConfigEntity config = existingOrNewConfig();
        config.setTotalCapital(BigDecimal.ZERO);

        assertThatThrownBy(() -> {
            userConfigRepository.save(config);
            entityManager.flush();
        }).isInstanceOf(org.hibernate.exception.ConstraintViolationException.class);
    }

    private UserConfigEntity existingOrNewConfig() {
        return userConfigRepository.findAll().stream().findFirst().orElseGet(UserConfigEntity::new);
    }
}
