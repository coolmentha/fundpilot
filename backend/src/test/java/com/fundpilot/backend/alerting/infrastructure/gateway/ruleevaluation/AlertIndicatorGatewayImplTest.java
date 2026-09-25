package com.fundpilot.backend.alerting.infrastructure.gateway.ruleevaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.alerting.application.gateway.ruleevaluation.AlertFundFactsGateway;
import com.fundpilot.backend.alerting.domain.condition.AlertCondition;
import com.fundpilot.backend.alerting.domain.condition.ConditionRelation;
import com.fundpilot.backend.alerting.domain.condition.IndicatorCode;
import com.fundpilot.backend.marketdata.adapter.api.indicatorcompute.MarketIndicatorComputeApi;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 指标取值的两条来源：基金事实类直接读快照，行情类按条件所需个数向 marketdata 取数。 */
class AlertIndicatorGatewayImplTest {

    private static final Instant END = Instant.parse("2026-09-21T00:00:00Z");

    private final MarketIndicatorComputeApi indicators = mock(MarketIndicatorComputeApi.class);
    private final AlertIndicatorGatewayImpl gateway = new AlertIndicatorGatewayImpl(indicators);

    @Test
    void 基金事实类条件直接取快照字段且不访问行情计算() {
        var fund = fund("0.031", "0.152");

        assertThat(gateway.values(fund, AlertCondition.of(IndicatorCode.DAILY_CHANGE, ConditionRelation.ABOVE),
                END)).containsExactly(new BigDecimal("0.031"));
        assertThat(gateway.values(fund, AlertCondition.of(IndicatorCode.HOLDING_RETURN, ConditionRelation.ABOVE),
                END)).containsExactly(new BigDecimal("0.152"));
        verifyNoInteractions(indicators);
    }

    @Test
    void 基金事实缺失时返回空列表() {
        var fund = fund(null, null);

        assertThat(gateway.values(fund, AlertCondition.of(IndicatorCode.DAILY_CHANGE, ConditionRelation.ABOVE),
                END)).isEmpty();
        assertThat(gateway.values(fund, AlertCondition.of(IndicatorCode.HOLDING_RETURN, ConditionRelation.ABOVE),
                END)).isEmpty();
    }

    @Test
    void 单值条件只取最新一个取值() {
        when(indicators.recent(any())).thenReturn(List.of(
                new MarketIndicatorComputeApi.IndicatorPoint(END.minusSeconds(86400), new BigDecimal("12.5"))));

        var values = gateway.values(fund("0.01", "0.02"),
                AlertCondition.of(IndicatorCode.INDEX_PE, ConditionRelation.BELOW), END);

        assertThat(values).containsExactly(new BigDecimal("12.5"));
        var request = captured();
        assertThat(request.count()).isEqualTo(1);
        assertThat(request.indicatorCode()).isEqualTo("INDEX_PE");
        assertThat(request.fundProductId()).isEqualTo(1102L);
        assertThat(request.endExclusive()).isEqualTo(END);
        assertThat(request.params()).isEmpty();
    }

    @Test
    void 事件型条件取两个取值并带上指标默认参数() {
        when(indicators.recent(any())).thenReturn(List.of(
                new MarketIndicatorComputeApi.IndicatorPoint(END.minusSeconds(86400), new BigDecimal("-0.05")),
                new MarketIndicatorComputeApi.IndicatorPoint(END, new BigDecimal("0.03"))));

        var values = gateway.values(fund("0.01", "0.02"),
                AlertCondition.of(IndicatorCode.MA_CROSS, ConditionRelation.CROSS_ABOVE), END);

        assertThat(values).containsExactly(new BigDecimal("-0.05"), new BigDecimal("0.03"));
        var request = captured();
        assertThat(request.count()).isEqualTo(2);
        assertThat(request.params()).containsEntry("fast", 20).containsEntry("slow", 50);
    }

    @Test
    void 未持仓基金不提供持仓收益率() {
        var fund = fund("0.031", "0.152", false);

        assertThat(gateway.values(fund, AlertCondition.of(IndicatorCode.HOLDING_RETURN, ConditionRelation.ABOVE),
                END)).isEmpty();
        assertThat(gateway.values(fund, AlertCondition.of(IndicatorCode.DAILY_CHANGE, ConditionRelation.ABOVE),
                END)).containsExactly(new BigDecimal("0.031"));
        verifyNoInteractions(indicators);
    }

    @Test
    void 行情数据不足时返回空列表() {
        when(indicators.recent(any())).thenReturn(List.of());

        assertThat(gateway.values(fund("0.01", "0.02"),
                AlertCondition.of(IndicatorCode.PRICE_VS_MA, ConditionRelation.BELOW), END)).isEmpty();
    }

    private MarketIndicatorComputeApi.ComputeRequest captured() {
        var captor = ArgumentCaptor.forClass(MarketIndicatorComputeApi.ComputeRequest.class);
        verify(indicators).recent(captor.capture());
        return captor.getValue();
    }

    private static AlertFundFactsGateway.AlertFundFact fund(String dailyChangePct, String holdingReturnRate) {
        return fund(dailyChangePct, holdingReturnRate, true);
    }

    private static AlertFundFactsGateway.AlertFundFact fund(String dailyChangePct, String holdingReturnRate,
                                                            boolean open) {
        return new AlertFundFactsGateway.AlertFundFact(1002L, 1102L, "161725", "招商中证白酒",
                open ? "OPEN" : "PENDING_HOLDING", open, "INDEX", END.minusSeconds(86400 * 30),
                new BigDecimal("1.1"), new BigDecimal("1000"), BigDecimal.ZERO,
                dailyChangePct == null ? null : new BigDecimal(dailyChangePct), new BigDecimal("1000"),
                new BigDecimal("50"), holdingReturnRate == null ? null : new BigDecimal(holdingReturnRate),
                new BigDecimal("1.2"), new BigDecimal("1.2"), new BigDecimal("2.4"), END.minusSeconds(86400 * 10),
                END, "READY");
    }
}