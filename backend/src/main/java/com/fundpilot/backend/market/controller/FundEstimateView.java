package com.fundpilot.backend.market.controller;

import com.fundpilot.backend.market.client.FundEstimateSnapshot;

import java.math.BigDecimal;

/**
 * 基金盘中估值视图 DTO(行情工作台基金列表的实时涨跌列)。
 *
 * @param estimatedChangePct 估算涨跌幅(小数,如 -0.0462 表 -4.62%;前端 ×100 显示)
 * @param estimateTime       估值时间(原始字符串,如 "2026-07-04 15:00")
 * @param baseNavDate        基准净值日期(估算所基于的已结算净值日期)
 */
public record FundEstimateView(
        BigDecimal estimatedChangePct,
        String estimateTime,
        String baseNavDate) {

    public static FundEstimateView from(FundEstimateSnapshot s) {
        return s == null ? null
                : new FundEstimateView(s.estimatedChangePct(), s.estimateTime(), s.baseNavDate());
    }
}
