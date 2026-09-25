package com.fundpilot.backend.alerting.domain.condition;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConditionEvaluatorTest {

    @Test
    void 每个指标的高于与低于关系在阈值处触发() {
        for (IndicatorCode indicator : IndicatorCode.values()) {
            BigDecimal step = step(indicator);
            if (indicator.supports(ConditionRelation.ABOVE)) {
                AlertCondition above = condition(indicator, ConditionRelation.ABOVE);
                BigDecimal threshold = above.effectiveThreshold();
                assertThat(ConditionEvaluator.satisfied(above, List.of(threshold)))
                        .as("%s 高于阈值应触发", indicator).isTrue();
                assertThat(ConditionEvaluator.satisfied(above, List.of(threshold.subtract(step))))
                        .as("%s 低于阈值不应触发", indicator).isFalse();
            }
            if (indicator.supports(ConditionRelation.BELOW)) {
                AlertCondition below = condition(indicator, ConditionRelation.BELOW);
                BigDecimal threshold = below.effectiveThreshold();
                assertThat(ConditionEvaluator.satisfied(below, List.of(threshold)))
                        .as("%s 低于阈值应触发", indicator).isTrue();
                assertThat(ConditionEvaluator.satisfied(below, List.of(threshold.add(step))))
                        .as("%s 高于阈值不应触发", indicator).isFalse();
            }
        }
    }

    @Test
    void 每个指标的放大与缩小只比较相邻两点() {
        for (IndicatorCode indicator : IndicatorCode.values()) {
            if (indicator.supports(ConditionRelation.INCREASING)) {
                AlertCondition increasing = AlertCondition.of(indicator, ConditionRelation.INCREASING);
                assertThat(ConditionEvaluator.satisfied(increasing, List.of(BigDecimal.ONE, BigDecimal.TWO)))
                        .as("%s 放大关系", indicator).isTrue();
                assertThat(ConditionEvaluator.satisfied(increasing, List.of(BigDecimal.TWO, BigDecimal.ONE)))
                        .as("%s 未放大", indicator).isFalse();
            }
            if (indicator.supports(ConditionRelation.DECREASING)) {
                AlertCondition decreasing = AlertCondition.of(indicator, ConditionRelation.DECREASING);
                assertThat(ConditionEvaluator.satisfied(decreasing, List.of(BigDecimal.TWO, BigDecimal.ONE)))
                        .as("%s 缩小关系", indicator).isTrue();
                assertThat(ConditionEvaluator.satisfied(decreasing, List.of(BigDecimal.ONE, BigDecimal.TWO)))
                        .as("%s 未缩小", indicator).isFalse();
            }
        }
    }

    @Test
    void 每个指标的上穿与下穿只在跨界当日触发() {
        for (IndicatorCode indicator : IndicatorCode.values()) {
            BigDecimal step = step(indicator);
            if (indicator.supports(ConditionRelation.CROSS_ABOVE)) {
                AlertCondition cross = AlertCondition.of(indicator, ConditionRelation.CROSS_ABOVE);
                BigDecimal threshold = cross.effectiveThreshold();
                assertThat(ConditionEvaluator.satisfied(cross, List.of(threshold.subtract(step),
                        threshold.add(step)))).as("%s 上穿当日应触发", indicator).isTrue();
                assertThat(ConditionEvaluator.satisfied(cross, List.of(threshold.add(step),
                        threshold.add(step.add(step))))).as("%s 上穿次日不应重复触发", indicator).isFalse();
            }
            if (indicator.supports(ConditionRelation.CROSS_BELOW)) {
                AlertCondition cross = AlertCondition.of(indicator, ConditionRelation.CROSS_BELOW);
                BigDecimal threshold = cross.effectiveThreshold();
                assertThat(ConditionEvaluator.satisfied(cross, List.of(threshold.add(step),
                        threshold.subtract(step)))).as("%s 下穿当日应触发", indicator).isTrue();
                assertThat(ConditionEvaluator.satisfied(cross, List.of(threshold.subtract(step),
                        threshold.subtract(step.add(step))))).as("%s 下穿次日不应重复触发", indicator).isFalse();
            }
        }
    }

    @Test
    void 取值不足时不触发() {
        for (IndicatorCode indicator : IndicatorCode.values()) {
            for (IndicatorRelation relation : indicator.relations()) {
                AlertCondition condition = condition(indicator, relation.relation());
                assertThat(ConditionEvaluator.satisfied(condition, List.of()))
                        .as("%s.%s 无取值", indicator, relation.relation()).isFalse();
                if (relation.relation().needsPrevious()) {
                    assertThat(ConditionEvaluator.satisfied(condition, List.of(BigDecimal.ONE)))
                            .as("%s.%s 只有一个取值", indicator, relation.relation()).isFalse();
                }
            }
        }
    }

    @Test
    void 需求取值个数由关系决定() {
        assertThat(ConditionEvaluator.requiredValues(AlertCondition.of(IndicatorCode.INDEX_PE,
                ConditionRelation.ABOVE))).isEqualTo(1);
        assertThat(ConditionEvaluator.requiredValues(AlertCondition.of(IndicatorCode.WEEKLY_MACD_HISTOGRAM,
                ConditionRelation.DECREASING))).isEqualTo(2);
        assertThat(ConditionEvaluator.requiredValues(AlertCondition.of(IndicatorCode.MA_CROSS,
                ConditionRelation.CROSS_BELOW))).isEqualTo(2);
    }

    @Test
    void 绿柱扩大需要绿柱与柱高缩小同时满足() {
        AlertCondition green = AlertCondition.of(IndicatorCode.WEEKLY_MACD_HISTOGRAM, ConditionRelation.BELOW);
        AlertCondition shrinking = AlertCondition.of(IndicatorCode.WEEKLY_MACD_HISTOGRAM,
                ConditionRelation.DECREASING);

        List<BigDecimal> expanding = List.of(new BigDecimal("-1"), new BigDecimal("-3"));
        assertThat(ConditionEvaluator.satisfied(green, expanding)).isTrue();
        assertThat(ConditionEvaluator.satisfied(shrinking, expanding)).isTrue();

        List<BigDecimal> narrowing = List.of(new BigDecimal("-3"), new BigDecimal("-1"));
        assertThat(ConditionEvaluator.satisfied(green, narrowing)).isTrue();
        assertThat(ConditionEvaluator.satisfied(shrinking, narrowing)).isFalse();
    }

    @Test
    void 跌破年线只在下穿当日触发() {
        AlertCondition belowMean = AlertCondition.of(IndicatorCode.PRICE_VS_MA, ConditionRelation.CROSS_BELOW);

        assertThat(ConditionEvaluator.satisfied(belowMean, List.of(new BigDecimal("0.01"),
                new BigDecimal("-0.01")))).isTrue();
        assertThat(ConditionEvaluator.satisfied(belowMean, List.of(new BigDecimal("-0.01"),
                new BigDecimal("-0.02")))).isFalse();
        assertThat(ConditionEvaluator.satisfied(belowMean, List.of(new BigDecimal("0.01"),
                new BigDecimal("0.02")))).isFalse();
    }

    @Test
    void 均线上升与下降按相邻两点判定() {
        AlertCondition rising = AlertCondition.of(IndicatorCode.MA, ConditionRelation.INCREASING);
        AlertCondition falling = AlertCondition.of(IndicatorCode.MA, ConditionRelation.DECREASING);

        assertThat(ConditionEvaluator.satisfied(rising, List.of(new BigDecimal("2.10"),
                new BigDecimal("2.20")))).isTrue();
        assertThat(ConditionEvaluator.satisfied(falling, List.of(new BigDecimal("2.10"),
                new BigDecimal("2.20")))).isFalse();
    }

    @Test
    void 存量涨跌与盈利口径与新条件等价() {
        AlertCondition rise = AlertCondition.of(IndicatorCode.DAILY_CHANGE, ConditionRelation.ABOVE,
                new BigDecimal("0.05"));
        AlertCondition drop = AlertCondition.of(IndicatorCode.DAILY_CHANGE, ConditionRelation.BELOW,
                new BigDecimal("-0.05"));
        AlertCondition profit = AlertCondition.of(IndicatorCode.HOLDING_RETURN, ConditionRelation.ABOVE,
                new BigDecimal("0.15"));

        assertThat(ConditionEvaluator.satisfied(rise, List.of(new BigDecimal("0.05")))).isTrue();
        assertThat(ConditionEvaluator.satisfied(rise, List.of(new BigDecimal("0.0499")))).isFalse();
        assertThat(ConditionEvaluator.satisfied(drop, List.of(new BigDecimal("-0.05")))).isTrue();
        assertThat(ConditionEvaluator.satisfied(drop, List.of(new BigDecimal("-0.0499")))).isFalse();
        assertThat(ConditionEvaluator.satisfied(drop, List.of(new BigDecimal("0.06")))).isFalse();
        assertThat(ConditionEvaluator.satisfied(profit, List.of(new BigDecimal("0.15")))).isTrue();
        assertThat(ConditionEvaluator.satisfied(profit, List.of(new BigDecimal("0.1499")))).isFalse();
    }

    @Test
    void 量能比值与PE分位按各自阈值判定() {
        AlertCondition heavyVolume = AlertCondition.of(IndicatorCode.VOLUME_RATIO, ConditionRelation.ABOVE,
                new BigDecimal("1.5"));
        AlertCondition cheap = AlertCondition.of(IndicatorCode.INDEX_PE_PERCENTILE, ConditionRelation.BELOW,
                new BigDecimal("0.2"));

        assertThat(ConditionEvaluator.satisfied(heavyVolume, List.of(new BigDecimal("1.62")))).isTrue();
        assertThat(ConditionEvaluator.satisfied(heavyVolume, List.of(new BigDecimal("1.48")))).isFalse();
        assertThat(ConditionEvaluator.satisfied(cheap, List.of(new BigDecimal("0.12")))).isTrue();
        assertThat(ConditionEvaluator.satisfied(cheap, List.of(new BigDecimal("0.35")))).isFalse();
    }

    @Test
    void 区间位置与回撤按阈值判定() {
        AlertCondition high = AlertCondition.of(IndicatorCode.NAV_RANGE_POSITION, ConditionRelation.ABOVE,
                new BigDecimal("0.9"));
        AlertCondition drawdown = AlertCondition.of(IndicatorCode.NAV_DRAWDOWN, ConditionRelation.ABOVE,
                new BigDecimal("0.08"));

        assertThat(ConditionEvaluator.satisfied(high, List.of(new BigDecimal("0.95")))).isTrue();
        assertThat(ConditionEvaluator.satisfied(high, List.of(new BigDecimal("0.88")))).isFalse();
        assertThat(ConditionEvaluator.satisfied(drawdown, List.of(new BigDecimal("0.11")))).isTrue();
        assertThat(ConditionEvaluator.satisfied(drawdown, List.of(new BigDecimal("0.03")))).isFalse();
    }

    /** 每个指标取一个足以跨越阈值又落在取值范围附近的步长，用于通用关系测试。 */
    private static BigDecimal step(IndicatorCode indicator) {
        return indicator.maximum().subtract(indicator.minimum()).divide(BigDecimal.valueOf(100));
    }

    /** 无默认阈值的指标（净值均线）用一个位于取值范围中间的阈值构造条件。 */
    private static AlertCondition condition(IndicatorCode indicator, ConditionRelation relation) {
        if (indicator.relation(relation).effectiveDefaultThreshold() != null) {
            return AlertCondition.of(indicator, relation);
        }
        return AlertCondition.of(indicator, relation,
                indicator.minimum().add(indicator.maximum()).divide(BigDecimal.TWO));
    }
}