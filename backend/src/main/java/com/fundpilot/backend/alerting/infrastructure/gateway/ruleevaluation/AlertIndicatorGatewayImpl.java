package com.fundpilot.backend.alerting.infrastructure.gateway.ruleevaluation;

import com.fundpilot.backend.alerting.application.gateway.ruleevaluation.AlertFundFactsGateway;
import com.fundpilot.backend.alerting.application.gateway.ruleevaluation.AlertIndicatorGateway;
import com.fundpilot.backend.alerting.domain.condition.AlertCondition;
import com.fundpilot.backend.alerting.domain.condition.ConditionEvaluator;
import com.fundpilot.backend.alerting.domain.condition.IndicatorSource;
import com.fundpilot.backend.marketdata.adapter.api.indicatorcompute.MarketIndicatorComputeApi;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 行情类指标经 marketdata 的按需计算取得；基金事实类指标直接读提醒评估当日的基金快照。 */
@Component
@RequiredArgsConstructor
public class AlertIndicatorGatewayImpl implements AlertIndicatorGateway {

    private final MarketIndicatorComputeApi indicators;

    @Override
    public List<BigDecimal> values(AlertFundFactsGateway.AlertFundFact fund, AlertCondition condition,
                                   Instant endExclusive) {
        if (condition.indicator().source() == IndicatorSource.FUND_FACT) {
            BigDecimal value = factValue(fund, condition);
            return value == null ? List.of() : List.of(value);
        }
        // 事件型与放大/缩小类条件需要前一个取值，按条件自行声明的最少个数取数
        return indicators.recent(new MarketIndicatorComputeApi.ComputeRequest(fund.fundProductId(),
                        condition.indicator().code(), condition.params(), endExclusive,
                        ConditionEvaluator.requiredValues(condition)))
                .stream().map(MarketIndicatorComputeApi.IndicatorPoint::value).toList();
    }

    /**
     * 基金事实类指标的当日取值；缺值返回 null，由调用方按「数据不足」处理。
     *
     * <p>持仓收益率只对已持仓基金有定义，未持仓时视为无数据，等价于旧模型里「盈利提醒仅对持仓基金生效」。
     */
    private static BigDecimal factValue(AlertFundFactsGateway.AlertFundFact fund, AlertCondition condition) {
        return switch (condition.indicator()) {
            case DAILY_CHANGE -> fund.dailyChangePct();
            case HOLDING_RETURN -> fund.open() ? fund.holdingReturnRate() : null;
            default -> null;
        };
    }
}