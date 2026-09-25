package com.fundpilot.backend.alerting.domain.condition;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundpilot.backend.marketdata.application.query.indicatorcompute.IndicatorComputeQueryHandler;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IndicatorCodeTest {

    @Test
    void 每个指标至少声明一条关系与中文说明() {
        for (IndicatorCode indicator : IndicatorCode.values()) {
            assertThat(indicator.relations()).as("%s 的关系", indicator).isNotEmpty();
            assertThat(indicator.label()).as("%s 的中文名", indicator).isNotBlank();
            assertThat(indicator.description()).as("%s 的说明", indicator).isNotBlank();
        }
    }

    @Test
    void 关系默认阈值落在指标取值范围内() {
        for (IndicatorCode indicator : IndicatorCode.values()) {
            for (IndicatorRelation relation : indicator.relations()) {
                BigDecimal threshold = relation.effectiveDefaultThreshold();
                if (threshold == null) {
                    continue;
                }
                assertThat(threshold).as("%s.%s 的默认阈值", indicator, relation.relation())
                        .isBetween(indicator.minimum(), indicator.maximum());
            }
        }
    }

    @Test
    void 参数默认值落在声明范围内且可按名检索() {
        for (IndicatorCode indicator : IndicatorCode.values()) {
            for (IndicatorParameter parameter : indicator.parameters()) {
                assertThat(parameter.defaultValue()).as("%s.%s 的默认值", indicator, parameter.name())
                        .isBetween(parameter.minimum(), parameter.maximum());
                assertThat(indicator.parameter(parameter.name())).isPresent();
            }
            assertThat(indicator.parameter("not-exist")).isEmpty();
        }
    }

    @Test
    void 行情类指标码与行情侧的按需计算清单一致() {
        Set<String> computable = IndicatorComputeQueryHandler.supportedCodes();

        for (IndicatorCode indicator : IndicatorCode.values()) {
            if (indicator.source() == IndicatorSource.MARKET_DATA) {
                assertThat(computable).as("%s 应在行情侧可计算", indicator).contains(indicator.code());
            } else {
                assertThat(computable).as("%s 取自提醒侧基金事实", indicator).doesNotContain(indicator.code());
            }
        }
    }
}