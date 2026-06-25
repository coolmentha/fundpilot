package com.fundpilot.backend.fund.service.support;

import com.fundpilot.backend.fund.enums.FundCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #5 验收:HardConstraintConfig 全局硬约束常量。
 * 验证值正确,且 {@code TIER_CLEAR_BUFFER} 是 final static(ADR-0003,不可调参数)。
 */
class HardConstraintConfigTest {

    @Test
    void buildRatioIsTenPercent() {
        assertThat(HardConstraintConfig.BUILD_RATIO).isEqualByComparingTo(new BigDecimal("0.10"));
    }

    @Test
    void tierClearBufferIsHalfPercentAndFinalStatic() throws NoSuchFieldException {
        assertThat(HardConstraintConfig.TIER_CLEAR_BUFFER).isEqualByComparingTo(new BigDecimal("0.005"));

        Field field = HardConstraintConfig.class.getDeclaredField("TIER_CLEAR_BUFFER");
        int modifiers = field.getModifiers();
        assertThat(Modifier.isFinal(modifiers)).isTrue();
        assertThat(Modifier.isStatic(modifiers)).isTrue();
    }

    @Test
    void categoryPositionLimitIsThirtyPercent() {
        assertThat(HardConstraintConfig.CATEGORY_POSITION_LIMIT).isEqualByComparingTo(new BigDecimal("0.30"));
    }

    @Test
    void totalEquityPositionLimitIsEightyPercent() {
        assertThat(HardConstraintConfig.TOTAL_EQUITY_POSITION_LIMIT).isEqualByComparingTo(new BigDecimal("0.80"));
    }

    @Test
    void singleAddRatioLimitIsFiftyPercent() {
        assertThat(HardConstraintConfig.SINGLE_ADD_RATIO_LIMIT).isEqualByComparingTo(new BigDecimal("0.50"));
    }

    @Test
    void minHoldDaysIsFiveTradingDays() {
        assertThat(HardConstraintConfig.MIN_HOLD_DAYS).isEqualTo(5);
    }

    static Stream<Arguments> singlePositionLimits() {
        return Stream.of(
                Arguments.of(FundCategory.BROAD_BASE, "0.20"),
                Arguments.of(FundCategory.SECTOR, "0.15"),
                Arguments.of(FundCategory.ACTIVE, "0.20"),
                Arguments.of(FundCategory.MIXED, "0.20")
        );
    }

    @ParameterizedTest
    @MethodSource("singlePositionLimits")
    void singlePositionLimitByCategory(FundCategory category, String expected) {
        assertThat(HardConstraintConfig.singlePositionLimit(category))
                .isEqualByComparingTo(new BigDecimal(expected));
    }
}
