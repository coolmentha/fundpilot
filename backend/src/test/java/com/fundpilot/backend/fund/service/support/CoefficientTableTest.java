package com.fundpilot.backend.fund.service.support;

import com.fundpilot.backend.market.enums.VolumeState;
import com.fundpilot.backend.market.enums.WeeklyMacdState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #5 验收:CoefficientTable 三维度独立查表(年线/MACD/成交量),
 * CoefficientCombiner 三维相乘再 clamp(0.3, 1.5)。数据来源 CONTEXT.md「调节系数表」。
 */
class CoefficientTableTest {

    static Stream<Arguments> yearLineCoefficients() {
        return Stream.of(
                Arguments.of(YearLineState.ABOVE_RISING, "1.0"),
                Arguments.of(YearLineState.ABOVE_FALLING, "0.7"),
                Arguments.of(YearLineState.BELOW_FALLING, "0.4")
        );
    }

    @ParameterizedTest
    @MethodSource("yearLineCoefficients")
    void yearLineCoefficient(YearLineState state, String expected) {
        assertThat(CoefficientTable.yearLine(state)).isEqualByComparingTo(new BigDecimal(expected));
    }

    static Stream<Arguments> macdCoefficients() {
        return Stream.of(
                Arguments.of(WeeklyMacdState.DIVERGENCE_BOTTOM, "1.2"),
                Arguments.of(WeeklyMacdState.GREEN_SHRINKING, "1.0"),
                Arguments.of(WeeklyMacdState.RED_SHRINKING, "0.9"),
                Arguments.of(WeeklyMacdState.GREEN_EXPANDING, "0.6")
        );
    }

    @ParameterizedTest
    @MethodSource("macdCoefficients")
    void macdCoefficient(WeeklyMacdState state, String expected) {
        assertThat(CoefficientTable.macd(state)).isEqualByComparingTo(new BigDecimal(expected));
    }

    static Stream<Arguments> volumeCoefficients() {
        return Stream.of(
                Arguments.of(VolumeState.LOW_STABLE, "1.2"),
                Arguments.of(VolumeState.NORMAL, "1.0"),
                Arguments.of(VolumeState.HIGH_DROP, "0.5")
        );
    }

    @ParameterizedTest
    @MethodSource("volumeCoefficients")
    void volumeCoefficient(VolumeState state, String expected) {
        assertThat(CoefficientTable.volume(state)).isEqualByComparingTo(new BigDecimal(expected));
    }

    @Test
    void combineClampsToLowerBoundWhenAllNegative() {
        // 年线 0.4 × MACD 0.6 × 成交量 0.5 = 0.12,截到 0.3
        BigDecimal combined = CoefficientCombiner.combine(
                new BigDecimal("0.4"), new BigDecimal("0.6"), new BigDecimal("0.5"));

        assertThat(combined).isEqualByComparingTo(new BigDecimal("0.3"));
    }

    @Test
    void combineClampsToUpperBoundWhenAllPositive() {
        // 年线 1.0 × MACD 1.2 × 成交量 1.2 = 1.44,未超 1.5,不截
        BigDecimal within = CoefficientCombiner.combine(
                new BigDecimal("1.0"), new BigDecimal("1.2"), new BigDecimal("1.2"));
        assertThat(within).isEqualByComparingTo(new BigDecimal("1.44"));

        // 1.2 × 1.2 × 1.2 = 1.728,截到 1.5
        BigDecimal clamped = CoefficientCombiner.combine(
                new BigDecimal("1.2"), new BigDecimal("1.2"), new BigDecimal("1.2"));
        assertThat(clamped).isEqualByComparingTo(new BigDecimal("1.5"));
    }

    @Test
    void combineReturnsProductWhenWithinBounds() {
        // 1.0 × 1.0 × 1.0 = 1.0
        assertThat(CoefficientCombiner.combine(
                new BigDecimal("1.0"), new BigDecimal("1.0"), new BigDecimal("1.0")))
                .isEqualByComparingTo(new BigDecimal("1.0"));
    }
}
