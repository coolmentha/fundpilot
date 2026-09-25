package com.fundpilot.backend.alerting.application.gateway.ruleevaluation;

import com.fundpilot.backend.alerting.domain.condition.AlertCondition;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 提醒条件求值所需的指标取值来源：行情类指标按需计算，基金事实类指标直接取当日快照字段。 */
public interface AlertIndicatorGateway {

    /**
     * 该基金在条件所需个数上的指标取值（按时间升序，最新在最后）。
     *
     * @param endExclusive 数据截止时间（不含），即提醒评估当日
     * @return 取值序列；数据不足或该指标对该基金不适用时返回空列表
     */
    List<BigDecimal> values(AlertFundFactsGateway.AlertFundFact fund, AlertCondition condition, Instant endExclusive);
}