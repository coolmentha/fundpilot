package com.fundpilot.backend.market.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * K 线/走势图视图 DTO(基金详情页行情 Tab)。
 *
 * <p>统一承载两种图表:
 * <ul>
 *   <li>ETF/指数基金({@code chartType="kline"}):{@code bars} 含完整 OHLCV,前端渲染蜡烛图 + 成交量</li>
 *   <li>主动/混合基金({@code chartType="nav"}):{@code bars} 仅 close 有值(累计净值),前端渲染折线图</li>
 * </ul>
 *
 * @param chartType 图表类型:kline=蜡烛图、nav=净值走势
 * @param benchmark 基准名称(如 "沪深300";ETF 时是跟踪指数,主动基金时是对比基准)
 * @param bars      K 线柱列表,按日期升序
 */
public record KlineView(String chartType, String benchmark, List<Bar> bars) {

    public record Bar(Instant date, BigDecimal open, BigDecimal close,
                      BigDecimal high, BigDecimal low, long volume) {
    }
}
