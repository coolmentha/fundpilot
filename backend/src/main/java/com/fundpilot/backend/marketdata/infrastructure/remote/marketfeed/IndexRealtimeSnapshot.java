package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import java.math.BigDecimal;

/**
 * 大盘指数实时行情快照:来自东方财富 push2.eastmoney.com 的 ulist.np 接口。
 *
 * <p>行情工作台顶部指数条的数据载体。f2/f3/f4 字段在原始响应中已 ÷100 缩放
 * (如 f2=404364 表 4043.64 点,f3=37 表 +0.37%),解析时还原为真实值。
 *
 * @param secid         secid 格式代码(如 "1.000001"),含市场前缀,用于回查 K 线
 * @param name          指数名称(如 "上证指数")
 * @param currentPrice  当前点位(已还原,如 4043.64)
 * @param changeAmount  涨跌额(已还原,如 14.74)
 * @param changePct     涨跌幅(小数,如 0.0037 表 +0.37%)
 * @param turnover      成交额(元,原值未缩放)
 */
public record IndexRealtimeSnapshot(
        String secid,
        String name,
        BigDecimal currentPrice,
        BigDecimal changeAmount,
        BigDecimal changePct,
        BigDecimal turnover) {
}
