package com.fundpilot.backend.alerting.application.condition;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundpilot.backend.alerting.domain.condition.AlertCondition;
import com.fundpilot.backend.alerting.domain.condition.ConditionGroup;
import com.fundpilot.backend.alerting.domain.condition.ConditionRelation;
import com.fundpilot.backend.alerting.domain.condition.IndicatorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 条件组的 JSON 读写：规则落库与提醒快照共用同一条编解码路径，必须可无损往返。 */
class AlertConditionJsonCodecTest {

    @Test
    void 多条件可无损往返() {
        var group = ConditionGroup.allOf(List.of(
                new AlertCondition(IndicatorCode.PRICE_VS_MA, Map.of("window", 120),
                        ConditionRelation.BELOW, new BigDecimal("0.02")),
                AlertCondition.of(IndicatorCode.MA_CROSS, ConditionRelation.CROSS_ABOVE)));

        assertThat(AlertConditionJsonCodec.read(AlertConditionJsonCodec.write(group))).isEqualTo(group);
    }

    @Test
    void 单条件可无损往返() {
        var group = ConditionGroup.single(AlertCondition.of(IndicatorCode.DAILY_CHANGE,
                ConditionRelation.ABOVE, new BigDecimal("0.05")));

        assertThat(AlertConditionJsonCodec.read(AlertConditionJsonCodec.write(group))).isEqualTo(group);
    }

    @Test
    void 可读取迁移脚本回填的历史口径() {
        var group = AlertConditionJsonCodec.read("""
                {"match":"ALL","conditions":[{"indicator":"DAILY_CHANGE","params":{},
                 "relation":"BELOW","value":-0.03}]}
                """);

        assertThat(group.conditions()).containsExactly(AlertCondition.of(IndicatorCode.DAILY_CHANGE,
                ConditionRelation.BELOW, new BigDecimal("-0.03")));
    }
}