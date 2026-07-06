package com.fundpilot.backend.market.controller;

import com.fundpilot.backend.market.client.IndexRealtimeSnapshot;

import java.math.BigDecimal;

/**
 * 指数实时行情视图 DTO(行情工作台指数条)。
 *
 * @param secid         secid(如 "1.000001"),前端用于跳转 K 线详情
 * @param name          指数名称(如 "上证指数")
 * @param currentPrice  当前点位
 * @param changeAmount  涨跌额
 * @param changePct     涨跌幅(小数,如 0.0037 表 +0.37%;前端 ×100 显示)
 * @param turnover      成交额(元)
 */
public record IndexRealtimeView(
        String secid,
        String name,
        BigDecimal currentPrice,
        BigDecimal changeAmount,
        BigDecimal changePct,
        BigDecimal turnover) {

    public static IndexRealtimeView from(IndexRealtimeSnapshot s) {
        return new IndexRealtimeView(s.secid(), s.name(), s.currentPrice(),
                s.changeAmount(), s.changePct(), s.turnover());
    }
}
