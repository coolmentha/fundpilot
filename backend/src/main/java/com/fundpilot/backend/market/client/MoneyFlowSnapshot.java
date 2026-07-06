package com.fundpilot.backend.market.client;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 全市场资金流向快照:北向资金实时净流入,来自东方财富 push2.eastmoney.com 的 kamt.rtmin 接口。
 *
 * <p>行情工作台底部「资金流向」组件的数据载体。北向资金按分钟推送(s2n 数组),
 * 取最后一条作为最新值。本期只做北向资金一项(板块级主力/超大单等资金在 {@link SectorSnapshot} 里随板块返回)。
 *
 * <p>注:原始设计设想的「主力/超大单/大单/中单/小单」全市场汇总是板块聚合值,
 * 实际接入发现东方财富的全市场汇总接口结构不稳定,故本期只保留北向资金这一项可靠数据。
 * 板块级资金流向(主力净流入)随板块列表一并返回,前端可单独展示。
 *
 * @param northboundNet 北向资金净流入(元,正=流入;来自 s2n 最后一条的合计字段)
 * @param snapshotTime  数据时间(解析自 s2n 最后一条的 "HH:MM" 字段,补全当日日期)
 */
public record MoneyFlowSnapshot(
        BigDecimal northboundNet,
        Instant snapshotTime) {
}
