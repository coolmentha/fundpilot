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
 * <p>UserConfig 仅保存可选的 {@code monthlyDcaBudget}；旧关注指数列由 MarketData 迁移保留。
 */
class UserConfigSchemaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EntityManager entityManager;

    @Autowired
    UserConfigRepository userConfigRepository;

    @Test
    @Transactional
    void userConfigPersistsMonthlyDcaBudget() {
        UserConfigEntity config = existingOrNewConfig();
        config.setMonthlyDcaBudget(new BigDecimal("1234.56"));

        UserConfigEntity saved = userConfigRepository.save(config);
        entityManager.flush();
        entityManager.clear();

        UserConfigEntity reloaded = userConfigRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getMonthlyDcaBudget()).isEqualByComparingTo("1234.56");
    }

    @Test
    @Transactional
    void monthlyDcaBudgetNullable_未配置时允许为空() {
        UserConfigEntity config = existingOrNewConfig();
        config.setMonthlyDcaBudget(null);

        UserConfigEntity saved = userConfigRepository.save(config);
        entityManager.flush();
        entityManager.clear();

        UserConfigEntity reloaded = userConfigRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getMonthlyDcaBudget()).isNull();
    }

    @Test
    @Transactional
    void activeUserConfig_数据库只允许一行() {
        UserConfigEntity first = existingOrNewConfig();
        first.setMonthlyDcaBudget(new BigDecimal("100"));
        userConfigRepository.saveAndFlush(first);

        UserConfigEntity second = new UserConfigEntity();
        second.setOwnerId(testActorId());
        second.setMonthlyDcaBudget(new BigDecimal("200"));

        assertThatThrownBy(() -> userConfigRepository.saveAndFlush(second))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void fundPositionWarningRatio_数据库拒绝超过100百分比() {
        FundEntity fund = new FundEntity();
        fund.setFundCode("510300");
        fund.setFundName("沪深300ETF");
        fund.setFundCategory(FundCategory.BROAD_BASE);
        fund.setPositionWarningRatio(new BigDecimal("1.01"));

        assertThatThrownBy(() -> {
            entityManager.persist(fund);
            entityManager.flush();
        }).isInstanceOf(org.hibernate.exception.ConstraintViolationException.class);
    }

    @Test
    @Transactional
    void monthlyDcaBudget_数据库拒绝非正值() {
        UserConfigEntity config = existingOrNewConfig();
        config.setMonthlyDcaBudget(BigDecimal.ZERO);

        assertThatThrownBy(() -> {
            userConfigRepository.save(config);
            entityManager.flush();
        }).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private UserConfigEntity existingOrNewConfig() {
        return userConfigRepository.findAllByOwnerId(testActorId()).stream().findFirst().orElseGet(() -> {
            UserConfigEntity config = new UserConfigEntity();
            config.setOwnerId(testActorId());
            return config;
        });
    }
}
