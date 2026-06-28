package com.fundpilot.backend.fund.service.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * issue #18 盈亏/涨跌纯算术单测(CONTEXT.md「今日涨跌/今日盈亏/总盈亏」)。
 * <p>所有计算统一用累计净值 accumulatedNav(分红除权不会让跌幅"虚高")。
 */
class FundPnlCalculatorTest {

    @Test
    void 今日涨跌幅_正常上涨() {
        // 1.20 → 1.26:涨幅 5%
        BigDecimal pct = FundPnlCalculator.dailyChangePct(new BigDecimal("1.26"), new BigDecimal("1.20"));

        assertThat(pct).isCloseTo(new BigDecimal("0.05"), within(new BigDecimal("0.0001")));
    }

    @Test
    void 今日涨跌幅_下跌() {
        // 1.20 → 1.14:跌幅 -5%
        BigDecimal pct = FundPnlCalculator.dailyChangePct(new BigDecimal("1.14"), new BigDecimal("1.20"));

        assertThat(pct).isCloseTo(new BigDecimal("-0.05"), within(new BigDecimal("0.0001")));
    }

    @Test
    void 今日涨跌幅_上一期净值为零_返回null防除零() {
        BigDecimal pct = FundPnlCalculator.dailyChangePct(new BigDecimal("1.20"), BigDecimal.ZERO);

        assertThat(pct).isNull();
    }

    @Test
    void 今日涨跌幅_任一入参为null_返回null() {
        assertThat(FundPnlCalculator.dailyChangePct(null, new BigDecimal("1.20"))).isNull();
        assertThat(FundPnlCalculator.dailyChangePct(new BigDecimal("1.20"), null)).isNull();
    }

    @Test
    void 今日盈亏_持仓上涨() {
        // 持仓 1000 份,1.20 → 1.26,盈亏 = 1000 × 0.06 = 60
        BigDecimal pnl = FundPnlCalculator.dailyPnl(
                new BigDecimal("1000"), new BigDecimal("1.26"), new BigDecimal("1.20"));

        assertThat(pnl).isCloseTo(new BigDecimal("60"), within(new BigDecimal("0.01")));
    }

    @Test
    void 今日盈亏_无持仓返回null() {
        // holdingShares = null 表示无持仓,今日盈亏无意义
        assertThat(FundPnlCalculator.dailyPnl(
                null, new BigDecimal("1.26"), new BigDecimal("1.20"))).isNull();
    }

    @Test
    void 今日盈亏_净值缺一期返回null() {
        assertThat(FundPnlCalculator.dailyPnl(
                new BigDecimal("1000"), null, new BigDecimal("1.20"))).isNull();
        assertThat(FundPnlCalculator.dailyPnl(
                new BigDecimal("1000"), new BigDecimal("1.26"), null)).isNull();
    }

    @Test
    void 总盈亏_盈利() {
        // 持仓 1000 份,最近净值 1.50,成本单价 1.20;市值 1500,成本 1200;盈 300
        // 公式:shares × (nav - costPerShare) = 1000 × (1.50 - 1.20) = 300
        BigDecimal pnl = FundPnlCalculator.totalPnl(
                new BigDecimal("1000"), new BigDecimal("1.50"), new BigDecimal("1.20"));

        assertThat(pnl).isCloseTo(new BigDecimal("300"), within(new BigDecimal("0.01")));
    }

    @Test
    void 总盈亏_亏损() {
        // 持仓 1000 份,最近净值 1.00,成本单价 1.20;市1000,成本1200;亏 -200
        // 公式:1000 × (1.00 - 1.20) = -200
        BigDecimal pnl = FundPnlCalculator.totalPnl(
                new BigDecimal("1000"), new BigDecimal("1.00"), new BigDecimal("1.20"));

        assertThat(pnl).isCloseTo(new BigDecimal("-200"), within(new BigDecimal("0.01")));
    }

    @Test
    void 总盈亏_无持仓或无净值返回null() {
        assertThat(FundPnlCalculator.totalPnl(null, new BigDecimal("1.50"), new BigDecimal("1.20"))).isNull();
        assertThat(FundPnlCalculator.totalPnl(new BigDecimal("1000"), null, new BigDecimal("1.20"))).isNull();
    }

    @Test
    void 总盈亏_成本单价为null返回null_因为成本未知() {
        // costPerShare=null:无法确定成本基准,总盈亏不可知
        BigDecimal pnl = FundPnlCalculator.totalPnl(
                new BigDecimal("1000"), new BigDecimal("1.50"), null);

        assertThat(pnl).isNull();
    }

    @Test
    void 分红除权场景_累计净值不跳水涨跌不虚高() {
        // 单位净值因分红跳 1.50→1.00,但累计净值连续 1.50→1.51→1.52。
        // 用累计净值算:今日涨跌 = (1.52 - 1.51)/1.51 ≈ +0.66%,不会因分红除权虚高。
        BigDecimal pct = FundPnlCalculator.dailyChangePct(new BigDecimal("1.52"), new BigDecimal("1.51"));

        assertThat(pct).isCloseTo(new BigDecimal("0.0066"), within(new BigDecimal("0.0001")));
    }
}
