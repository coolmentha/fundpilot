package com.fundpilot.backend.market.controller;

import com.fundpilot.backend.market.client.MoneyFlowSnapshot;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 资金流向视图 DTO(行情工作台资金流向组件)。
 *
 * <p>本期只含北向资金一项(全市场主力/超大单等汇总接口不稳定,见 design-technical.md)。
 * 板块级主力资金在 {@link SectorView#mainforceNet()} 中随板块返回。
 *
 * @param northboundNet 北向资金净流入(元,正=流入)
 * @param snapshotTime  数据时间
 */
public record MoneyFlowView(
        BigDecimal northboundNet,
        Instant snapshotTime) {

    public static MoneyFlowView from(MoneyFlowSnapshot s) {
        return s == null ? null : new MoneyFlowView(s.northboundNet(), s.snapshotTime());
    }
}
