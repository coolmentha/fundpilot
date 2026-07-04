package com.fundpilot.backend.fund.service.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HardConstraintChecker 四条硬约束统一入口,返 {@code List<Breach>}(空=通过)。
 * <p>四条:buildRatio / singlePositionLimit / categoryPositionLimit / singleAddRatioLimit。
 * 读 {@link HardConstraintConfig} 上限。MIN_HOLD_DAYS 判定留给信号引擎,本期不在此检查。
 *
 * <p>行情工作台转向后移除了第五条 totalEquityPositionLimit(分母 totalInvestableCapital 已删,
 * 原 check5 降为 check4)。
 */
class HardConstraintCheckerTest {

    @Test
    void returnsEmptyWhenAllWithinLimits() {
        List<Breach> breaches = HardConstraintChecker.check4(
                new BigDecimal("0.10"),   // = BUILD_RATIO,不超
                new BigDecimal("0.30"),   // = 单只上限 30%,不超
                new BigDecimal("0.30"),   // = 单类上限 30%,不超
                new BigDecimal("0.50"));  // = 单次加仓 50%,不超

        assertThat(breaches).isEmpty();
    }

    @Test
    void flagsBuildRatioExceedingLimit() {
        List<Breach> breaches = HardConstraintChecker.check4(
                new BigDecimal("0.11"), new BigDecimal("0.30"), new BigDecimal("0.30"),
                new BigDecimal("0.50"));

        assertThat(breaches).singleElement().satisfies(b -> {
            assertThat(b.name()).isEqualTo("BUILD_RATIO");
            assertThat(b.actual()).isEqualByComparingTo(new BigDecimal("0.11"));
            assertThat(b.limit()).isEqualByComparingTo(new BigDecimal("0.10"));
        });
    }

    @Test
    void flagsSinglePositionExceedingLimit() {
        List<Breach> breaches = HardConstraintChecker.check4(
                new BigDecimal("0.10"), new BigDecimal("0.31"), new BigDecimal("0.30"),
                new BigDecimal("0.50"));

        assertThat(breaches).singleElement().satisfies(b -> {
            assertThat(b.name()).isEqualTo("SINGLE_POSITION_LIMIT");
            assertThat(b.actual()).isEqualByComparingTo(new BigDecimal("0.31"));
            assertThat(b.limit()).isEqualByComparingTo(new BigDecimal("0.30"));
        });
    }

    @Test
    void flagsCategoryPositionExceedingLimit() {
        List<Breach> breaches = HardConstraintChecker.check4(
                new BigDecimal("0.10"), new BigDecimal("0.30"), new BigDecimal("0.31"),
                new BigDecimal("0.50"));

        assertThat(breaches).singleElement().satisfies(b -> {
            assertThat(b.name()).isEqualTo("CATEGORY_POSITION_LIMIT");
            assertThat(b.limit()).isEqualByComparingTo(new BigDecimal("0.30"));
        });
    }

    @Test
    void flagsSingleAddRatioExceedingLimit() {
        List<Breach> breaches = HardConstraintChecker.check4(
                new BigDecimal("0.10"), new BigDecimal("0.30"), new BigDecimal("0.30"),
                new BigDecimal("0.51"));

        assertThat(breaches).singleElement().satisfies(b -> {
            assertThat(b.name()).isEqualTo("SINGLE_ADD_RATIO_LIMIT");
            assertThat(b.limit()).isEqualByComparingTo(new BigDecimal("0.50"));
        });
    }

    @Test
    void flagsAllFourWhenAllExceeded() {
        List<Breach> breaches = HardConstraintChecker.check4(
                new BigDecimal("0.11"), new BigDecimal("0.31"), new BigDecimal("0.31"),
                new BigDecimal("0.51"));

        assertThat(breaches).hasSize(4);
        assertThat(breaches).extracting(Breach::name).containsExactlyInAnyOrder(
                "BUILD_RATIO", "SINGLE_POSITION_LIMIT", "CATEGORY_POSITION_LIMIT", "SINGLE_ADD_RATIO_LIMIT");
    }
}
