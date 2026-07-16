package com.fundpilot.backend.fund.service.support;

import java.math.BigDecimal;

/**
 * 三态今日涨跌判定结果(issue #38)。
 *
 * @param todayChangePct 今日涨跌幅(估值前=0、估值阶段=fundgz、净值落库后=实际值;null 表示无数据)
 * @param isEstimated    是否估算态(true=fundgz 估算,false=估值前0/净值实际/降级)
 */
public record DailyChangeResult(BigDecimal todayChangePct, boolean isEstimated) {
}
