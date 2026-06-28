package com.fundpilot.backend.fund.service.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #5 验收:HardConstraintChecker 五条硬约束统一入口,返 {@code List<Breach>}(空=通过)。
 * <p>五条:buildRatio / singlePositionLimit / categoryPositionLimit / totalEquityPositionLimit / singleAddRatioLimit。
 * 读 {@link HardConstraintConfig} 上限。MIN_HOLD_DAYS 判定留给信号引擎(issue #12),本期不在此检查。
 * <p>singlePositionLimit 已统一 30% 无关类型(不再按 fundCategory 区分),check5 不再收 category 参数。
 */
class HardConstraintCheckerTest {

    @Test
    void returnsEmptyWhenAllWithinLimits() {
        List<Breach> breaches = HardConstraintChecker.check5(
                new BigDecimal("0.10"),   // = BUILD_RATIO,不超
                new BigDecimal("0.30"),   // = 单只上限 30%,不超
                new BigDecimal("0.30"),   // = 单类上限 30%,不超
                new BigDecimal("0.80"),   // = 总仓位 80%,不超
                new BigDecimal("0.50"));  // = 单次加仓 50%,不超

        assertThat(breaches).isEmpty();
    }

    @Test
    void flagsBuildRatioExceedingLimit() {
        List<Breach> breaches = HardConstraintChecker.check5(
                new BigDecimal("0.11"), new BigDecimal("0.30"), new BigDecimal("0.30"),
                new BigDecimal("0.80"), new BigDecimal("0.50"));

        assertThat(breaches).singleElement().satisfies(b -> {
            assertThat(b.name()).isEqualTo("BUILD_RATIO");
            assertThat(b.actual()).isEqualByComparingTo(new BigDecimal("0.11"));
            assertThat(b.limit()).isEqualByComparingTo(new BigDecimal("0.10"));
        });
    }

    @Test
    void flagsSinglePositionExceedingLimit() {
        // 单只上限 30%(无关类型)
        List<Breach> breaches = HardConstraintChecker.check5(
                new BigDecimal("0.10"), new BigDecimal("0.31"), new BigDecimal("0.30"),
                new BigDecimal("0.80"), new BigDecimal("0.50"));

        assertThat(breaches).singleElement().satisfies(b -> {
            assertThat(b.name()).isEqualTo("SINGLE_POSITION_LIMIT");
            assertThat(b.actual()).isEqualByComparingTo(new BigDecimal("0.31"));
            assertThat(b.limit()).isEqualByComparingTo(new BigDecimal("0.30"));
        });
    }

    @Test
    void flagsCategoryPositionExceedingLimit() {
        List<Breach> breaches = HardConstraintChecker.check5(
                new BigDecimal("0.10"), new BigDecimal("0.30"), new BigDecimal("0.31"),
                new BigDecimal("0.80"), new BigDecimal("0.50"));

        assertThat(breaches).singleElement().satisfies(b -> {
            assertThat(b.name()).isEqualTo("CATEGORY_POSITION_LIMIT");
            assertThat(b.limit()).isEqualByComparingTo(new BigDecimal("0.30"));
        });
    }

    @Test
    void flagsTotalEquityPositionExceedingLimit() {
        List<Breach> breaches = HardConstraintChecker.check5(
                new BigDecimal("0.10"), new BigDecimal("0.30"), new BigDecimal("0.30"),
                new BigDecimal("0.81"), new BigDecimal("0.50"));

        assertThat(breaches).singleElement().satisfies(b -> {
            assertThat(b.name()).isEqualTo("TOTAL_EQUITY_POSITION_LIMIT");
            assertThat(b.limit()).isEqualByComparingTo(new BigDecimal("0.80"));
        });
    }

    @Test
    void flagsSingleAddRatioExceedingLimit() {
        List<Breach> breaches = HardConstraintChecker.check5(
                new BigDecimal("0.10"), new BigDecimal("0.30"), new BigDecimal("0.30"),
                new BigDecimal("0.80"), new BigDecimal("0.51"));

        assertThat(breaches).singleElement().satisfies(b -> {
            assertThat(b.name()).isEqualTo("SINGLE_ADD_RATIO_LIMIT");
            assertThat(b.limit()).isEqualByComparingTo(new BigDecimal("0.50"));
        });
    }

    @Test
    void flagsAllFiveWhenAllExceeded() {
        List<Breach> breaches = HardConstraintChecker.check5(
                new BigDecimal("0.11"), new BigDecimal("0.31"), new BigDecimal("0.31"),
                new BigDecimal("0.81"), new BigDecimal("0.51"));

        assertThat(breaches).hasSize(5);
        assertThat(breaches).extracting(Breach::name).containsExactlyInAnyOrder(
                "BUILD_RATIO", "SINGLE_POSITION_LIMIT", "CATEGORY_POSITION_LIMIT",
                "TOTAL_EQUITY_POSITION_LIMIT", "SINGLE_ADD_RATIO_LIMIT");
    }
}
