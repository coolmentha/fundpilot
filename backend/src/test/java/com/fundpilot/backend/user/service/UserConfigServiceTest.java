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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class UserConfigServiceTest extends AbstractIntegrationTest {

    @Autowired UserConfigService userConfigService;
    @Autowired UserConfigRepository userConfigRepository;

    @BeforeEach
    void cleanConfig() {
        userConfigRepository.findAll().stream().findFirst().ifPresent(config -> {
            config.setTotalCapital(null);
            config.setWatchedIndices(null);
            userConfigRepository.save(config);
        });
    }

    @Test
    void deposit_连续入金累加到同一总资金池() {
        userConfigService.deposit(new BigDecimal("10000"));
        var view = userConfigService.deposit(new BigDecimal("2500.50"));

        assertThat(view.totalCapital()).isEqualByComparingTo("12500.50");
        assertThat(userConfigRepository.count()).isEqualTo(1);
    }

    @Test
    void deposit_非正金额拒绝() {
        assertThatThrownBy(() -> userConfigService.deposit(BigDecimal.ZERO))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.DEPOSIT_AMOUNT_INVALID.name());
    }

    @Test
    void deposit_超过数据库金额精度时返回业务错误() {
        assertThatThrownBy(() -> userConfigService.deposit(new BigDecimal("100000000000")))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.DEPOSIT_AMOUNT_INVALID.name());
    }
}
