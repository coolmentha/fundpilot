package com.fundpilot.backend.user.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import com.fundpilot.backend.user.repository.UserConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class UserConfigServiceTest extends AbstractIntegrationTest {

    @Autowired UserConfigService userConfigService;
    @Autowired UserConfigRepository userConfigRepository;

    @BeforeEach
    void cleanConfig() {
        userConfigRepository.findAll().stream().findFirst().ifPresent(config -> {
            config.setMonthlyDcaBudget(null);
            config.setWatchedIndices(null);
            userConfigRepository.save(config);
        });
    }

    @Test
    void update_设置月度预算后覆盖旧值() {
        userConfigService.update(List.of("1.000001"), new BigDecimal("10000"));
        var view = userConfigService.update(List.of("1.000001"), new BigDecimal("2500.50"));

        assertThat(view.monthlyDcaBudget()).isEqualByComparingTo("2500.50");
        assertThat(userConfigRepository.count()).isEqualTo(1);
    }

    @Test
    void update_清空月度预算允许() {
        userConfigService.update(List.of(), new BigDecimal("2500"));

        var view = userConfigService.update(List.of(), null);

        assertThat(view.monthlyDcaBudget()).isNull();
    }

    @Test
    void update_非正月度预算拒绝() {
        assertThatThrownBy(() -> userConfigService.update(null, BigDecimal.ZERO))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.MONTHLY_DCA_BUDGET_INVALID.name());
    }

    @Test
    void update_超过数据库金额精度时返回业务错误() {
        assertThatThrownBy(() -> userConfigService.update(null, new BigDecimal("100000000000")))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.MONTHLY_DCA_BUDGET_INVALID.name());
    }
}
