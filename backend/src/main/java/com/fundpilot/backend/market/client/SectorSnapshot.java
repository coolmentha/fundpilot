package com.fundpilot.backend.market.client;

import java.math.BigDecimal;

/**
 * 行业板块涨跌快照:来自东方财富 push2.eastmoney.com 的 clist 接口(fs=m:90 t:2 行业板块)。
 *
 * <p>行情工作台底部「行业板块涨跌」组件的数据载体。f3 涨跌幅在原始响应中 ÷100 缩放
 * (如 f3=-22 表 -0.22%),f6 成交额为元原值。
 *
 * @param sectorCode   板块代码(如 "BK0420")
 * @param sectorName   板块名称(如 "航空机场")
 * @param changePct    今日涨跌幅(小数,如 -0.0022 表 -0.22%)
 * @param turnover     成交额(元,原值)
 * @param mainforceNet 主力净流入(元,来自 f62,正=流入;无资金字段时为 null)
 */
public record SectorSnapshot(
        String sectorCode,
        String sectorName,
        BigDecimal changePct,
        BigDecimal turnover,
        BigDecimal mainforceNet) {
}
