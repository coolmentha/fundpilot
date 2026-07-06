package com.fundpilot.backend.market.controller;

import com.fundpilot.backend.market.client.SectorSnapshot;

import java.math.BigDecimal;

/**
 * 行业板块涨跌视图 DTO(行情工作台板块涨跌组件)。
 *
 * @param sectorName   板块名称(如 "半导体")
 * @param changePct    今日涨跌幅(小数;前端 ×100 显示)
 * @param turnover     成交额(元)
 * @param mainforceNet 主力净流入(元,正=流入;null 表示无资金数据)
 */
public record SectorView(
        String sectorName,
        BigDecimal changePct,
        BigDecimal turnover,
        BigDecimal mainforceNet) {

    public static SectorView from(SectorSnapshot s) {
        return new SectorView(s.sectorName(), s.changePct(), s.turnover(), s.mainforceNet());
    }
}
