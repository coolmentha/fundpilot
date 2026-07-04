package com.fundpilot.backend.user.entity;

import com.fundpilot.backend.support.AbstractIntegrationTest;
import com.fundpilot.backend.user.repository.UserConfigRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * user_config 表 + UserConfigEntity schema 对齐验证。
 *
 * <p>行情工作台转向后,UserConfig 只存 {@code watchedIndices}(关注指数 secid 列表)。
 * 历史的 totalInvestableCapital 已随 V9 迁移删除。
 */
class UserConfigSchemaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EntityManager entityManager;

    @Autowired
    UserConfigRepository userConfigRepository;

    @Test
    @Transactional
    void userConfigPersistsWatchedIndices() {
        UserConfigEntity config = new UserConfigEntity();
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
        UserConfigEntity config = new UserConfigEntity();
        // watchedIndices 不设(null),用默认指数列表由服务层兜底

        UserConfigEntity saved = userConfigRepository.save(config);
        entityManager.flush();
        entityManager.clear();

        UserConfigEntity reloaded = userConfigRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getWatchedIndices()).isNull();
    }
}
