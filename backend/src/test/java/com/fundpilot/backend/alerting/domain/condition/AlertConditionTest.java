package com.fundpilot.backend.alerting.domain.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AlertConditionTest {

    @Test
    void 未填阈值时取指标默认值() {
        AlertCondition condition = AlertCondition.of(IndicatorCode.DAILY_CHANGE, ConditionRelation.ABOVE);

        assertThat(condition.effectiveThreshold()).isEqualByComparingTo("0.05");
        assertThat(condition.params()).isEmpty();
    }

    @Test
    void 显式阈值被原样保留() {
        AlertCondition condition = AlertCondition.of(IndicatorCode.HOLDING_RETURN, ConditionRelation.ABOVE,
                new BigDecimal("0.30"));

        assertThat(condition.effectiveThreshold()).isEqualByComparingTo("0.30");
    }

    @Test
    void 阈值超出取值范围被拒() {
        assertThatIllegalArgumentException().isThrownBy(() -> AlertCondition.of(IndicatorCode.DAILY_CHANGE,
                ConditionRelation.ABOVE, new BigDecimal("2")));
        assertThatIllegalArgumentException().isThrownBy(() -> AlertCondition.of(IndicatorCode.NAV_RANGE_POSITION,
                ConditionRelation.ABOVE, new BigDecimal("1.5")));
        assertThatIllegalArgumentException().isThrownBy(() -> AlertCondition.of(IndicatorCode.INDEX_PE_PERCENTILE,
                ConditionRelation.BELOW, new BigDecimal("-0.1")));
    }

    @Test
    void 指标不支持的关系被拒() {
        assertThatIllegalArgumentException().isThrownBy(() -> AlertCondition.of(IndicatorCode.DAILY_CHANGE,
                ConditionRelation.CROSS_ABOVE));
        assertThatIllegalArgumentException().isThrownBy(() -> AlertCondition.of(IndicatorCode.INDEX_PE,
                ConditionRelation.INCREASING));
        assertThatIllegalArgumentException().isThrownBy(() -> AlertCondition.of(IndicatorCode.NAV_RANGE_POSITION,
                ConditionRelation.CROSS_BELOW));
    }

    @Test
    void 未声明的参数被拒() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AlertCondition(IndicatorCode.PRICE_VS_MA,
                Map.of("period", 20), ConditionRelation.BELOW, null));
    }

    @Test
    void 参数越界被拒() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AlertCondition(IndicatorCode.PRICE_VS_MA,
                Map.of("window", 4), ConditionRelation.BELOW, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new AlertCondition(IndicatorCode.PRICE_VS_MA,
                Map.of("window", 300), ConditionRelation.BELOW, null));
    }

    @Test
    void 未填参数时补齐默认值() {
        AlertCondition single = new AlertCondition(IndicatorCode.PRICE_VS_MA, null,
                ConditionRelation.BELOW, null);
        AlertCondition cross = AlertCondition.of(IndicatorCode.MA_CROSS, ConditionRelation.CROSS_ABOVE);

        assertThat(single.params()).containsExactlyEntriesOf(Map.of("window", 250));
        assertThat(cross.params()).containsExactlyInAnyOrderEntriesOf(Map.of("fast", 20, "slow", 50));
    }

    @Test
    void 快线窗口不小于慢线窗口被拒() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AlertCondition(IndicatorCode.MA_CROSS,
                Map.of("fast", 60, "slow", 50), ConditionRelation.CROSS_ABOVE, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new AlertCondition(
                IndicatorCode.WEEKLY_MACD_HISTOGRAM, Map.of("fast", 30, "slow", 26),
                ConditionRelation.CROSS_ABOVE, null));
    }

    @Test
    void 必填阈值缺失被拒且补上后通过() {
        assertThatIllegalArgumentException().isThrownBy(() -> AlertCondition.of(IndicatorCode.MA,
                ConditionRelation.ABOVE));

        AlertCondition condition = AlertCondition.of(IndicatorCode.MA, ConditionRelation.ABOVE,
                new BigDecimal("2.5"));
        assertThat(condition.effectiveThreshold()).isEqualByComparingTo("2.5");
    }

    @Test
    void 上穿下穿的隐含界线为零() {
        AlertCondition golden = AlertCondition.of(IndicatorCode.WEEKLY_MACD_HISTOGRAM,
                ConditionRelation.CROSS_ABOVE);

        assertThat(golden.effectiveThreshold()).isEqualByComparingTo("0");
        assertThat(golden.indicator().relation(ConditionRelation.CROSS_ABOVE).alias()).isEqualTo("金叉");
    }

    @Test
    void 放大缩小类关系不接受阈值() {
        AlertCondition condition = AlertCondition.of(IndicatorCode.VOLUME_RATIO, ConditionRelation.INCREASING,
                new BigDecimal("0.5"));

        assertThat(condition.value()).isNull();
        assertThat(condition.effectiveThreshold()).isNull();
    }

    @Test
    void 指标码解析忽略大小写且未知指标被拒() {
        assertThat(IndicatorCode.find(" price_vs_ma ")).contains(IndicatorCode.PRICE_VS_MA);
        assertThat(IndicatorCode.find("NOT_EXIST")).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> IndicatorCode.of("NOT_EXIST"));
    }
}