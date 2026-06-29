package com.fundpilot.backend.market.client;

import java.math.BigDecimal;

/**
 * 基金盘中估值快照(issue #36):来自东方财富 fundgz 接口。
 *
 * <p>fundgz 返回盘中估算净值与涨跌幅,是三态今日涨跌「盘中态」的数据源。
 * gszzl 是基于单位净值的涨跌幅,但涨跌幅是比例(同日不除权,单位/累计净值涨跌幅一致),
 * 直接用作估算涨跌幅无口径问题。
 *
 * @param estimatedChangePct 估算涨跌幅(小数,如 -0.0462 表 -4.62%,来自 gszzl/100)
 * @param estimateTime        估值时间(原始字符串,如 "2026-06-26 15:00",来自 gztime)
 * @param baseNavDate         基准净值日期(估算所基于的已结算净值日期,来自 jzrq)
 */
public record FundEstimateSnapshot(
        BigDecimal estimatedChangePct,
        String estimateTime,
        String baseNavDate) {
}
