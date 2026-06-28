package com.fundpilot.backend.fund.service.support;

import java.math.BigDecimal;

/**
 * 三态今日涨跌判定结果(issue #38)。
 *
 * @param todayChangePct 今日涨跌幅(盘前=0、盘中=fundgz估值、盘后=落库净值算;null 表示无数据)
 * @param isEstimated    是否估算态(true=盘中 fundgz 估算,false=盘前0/盘后实际/降级落库)
 */
public record DailyChangeResult(BigDecimal todayChangePct, boolean isEstimated) {
}
