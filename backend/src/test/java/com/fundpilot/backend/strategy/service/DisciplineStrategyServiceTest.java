package com.fundpilot.backend.strategy.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.enums.FundSubType;
import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import com.fundpilot.backend.market.enums.VolumeState;
import com.fundpilot.backend.market.enums.WeeklyMacdState;
import com.fundpilot.backend.signal.enums.MeasureUnit;
import com.fundpilot.backend.signal.enums.SignalReason;
import com.fundpilot.backend.signal.enums.SignalType;
import com.fundpilot.backend.signal.enums.SignalWarning;
import com.fundpilot.backend.signal.enums.SignalWarningValue;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import com.fundpilot.backend.strategy.service.support.CapitalContext;
import com.fundpilot.backend.strategy.service.support.MarketIndicators;
import com.fundpilot.backend.strategy.service.support.SignalResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 信号引擎测试(卖出纪律专用)。金字塔加仓机制退场后,evaluateSignal 仅产出 NONE/SELL:
 * 状态门控(CLEARED/PENDING_HOLDING/无策略)与 SELL 两分支(逻辑止损 / 移动止盈)。
 * <p>定投止盈在整仓盈利启动后按周期高点回撤收割部分浮盈，并保留长期底仓。
 */
class DisciplineStrategyServiceTest {

    private final DisciplineStrategyService service = new DisciplineStrategyService();

    // ---- 状态门控 ----

    @Test
    void 状态CLEARED_返回NONE_FUND_CLEARED() {
        FundEntity fund = fund(FundStatus.CLEARED);

        SignalResult result = service.evaluateSignal(fund, strategy(), market(), capital(), Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.NONE);
        assertThat(result.reason()).isEqualTo(SignalReason.FUND_CLEARED);
    }

    @Test
    void 策略为null_返回NONE_NO_STRATEGY() {
        FundEntity fund = fund(FundStatus.HOLDING);

        SignalResult result = service.evaluateSignal(fund, null, market(), capital(), Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.NONE);
        assertThat(result.reason()).isEqualTo(SignalReason.NO_STRATEGY);
    }

    @Test
    void 策略状态非EFFECTIVE_返回NONE_NO_STRATEGY() {
        FundEntity fund = fund(FundStatus.HOLDING);
        FundStrategyEntity strategy = strategy();
        strategy.setStatus(StrategyParamStatus.CALIBRATED);

        SignalResult result = service.evaluateSignal(fund, strategy, market(), capital(), Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.NONE);
        assertThat(result.reason()).isEqualTo(SignalReason.NO_STRATEGY);
    }

    @Test
    void 策略状态PENDING_CALIBRATION_返回NONE_NO_STRATEGY() {
        FundEntity fund = fund(FundStatus.HOLDING);
        FundStrategyEntity strategy = strategy();
        strategy.setStatus(StrategyParamStatus.PENDING_CALIBRATION);

        SignalResult result = service.evaluateSignal(fund, strategy, market(), capital(), Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.NONE);
        assertThat(result.reason()).isEqualTo(SignalReason.NO_STRATEGY);
    }

    @Test
    void 状态PENDING_HOLDING_不再给建仓建议_返回NONE_NO_STRATEGY() {
        FundEntity fund = fund(FundStatus.PENDING_HOLDING);

        SignalResult result = service.evaluateSignal(fund, strategy(), market(), capital(), Instant.now(), 100);

        // 金字塔退场:买入由用户手动/定投决定,引擎不再产出 BUILD
        assertThat(result.signalType()).isEqualTo(SignalType.NONE);
        assertThat(result.reason()).isEqualTo(SignalReason.NO_STRATEGY);
    }

    // ---- 逻辑止损 ----

    @Test
    void 逻辑止损_ETF三条件全满足_返回SELL_LOGIC_BROKEN_一次清空() {
        FundEntity fund = fund(FundStatus.HOLDING);
        // 破年线 + MACD绿柱扩大 + 跟踪指数放量下跌
        MarketIndicators market = new MarketIndicators(
                new BigDecimal("0.9"), false, false,
                WeeklyMacdState.GREEN_EXPANDING, VolumeState.NORMAL,
                BigDecimal.ZERO, false, VolumeState.HIGH_DROP, true);
        CapitalContext capital = capital(new BigDecimal("1.0"), new BigDecimal("1.0"), new BigDecimal("100"));

        SignalResult result = service.evaluateSignal(fund, strategy(), market, capital, Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.SELL);
        assertThat(result.reason()).isEqualTo(SignalReason.LOGIC_BROKEN);
        // 一次清空:全卖 holdingShares
        assertThat(result.suggestedMeasure().getValue()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(result.suggestedMeasure().getMeasureUnit()).isEqualTo(MeasureUnit.SHARE);
    }

    @Test
    void 逻辑止损_ETF仅破年线未放量_不触发() {
        FundEntity fund = fund(FundStatus.HOLDING);
        // currentNav=1.0=holdingPeriodPeakNav,移动止盈无回落不触发;破年线由 priceAboveYearLine=false 控制
        MarketIndicators market = new MarketIndicators(
                new BigDecimal("1.0"), false, false,
                WeeklyMacdState.GREEN_EXPANDING, VolumeState.NORMAL,
                BigDecimal.ZERO, false, VolumeState.NORMAL, true); // 跟踪指数未放量
        CapitalContext capital = capital(new BigDecimal("1.0"), new BigDecimal("1.0"), new BigDecimal("100"));

        SignalResult result = service.evaluateSignal(fund, strategy(), market, capital, Instant.now(), 100);

        assertThat(result.signalType()).isNotEqualTo(SignalType.SELL);
        assertThat(result.reason()).isEqualTo(SignalReason.NO_SELL_TRIGGER);
    }

    @Test
    void 逻辑止损_主动基金破年线加MACD绿柱扩大_返回SELL_LOGIC_BROKEN() {
        FundEntity fund = fund(FundStatus.HOLDING);
        fund.setFundSubType(FundSubType.ACTIVE);
        // 主动基金:破年线 + MACD绿柱扩大即触发(原 weeklyCoolDown 第三条件随金字塔移除)
        MarketIndicators market = new MarketIndicators(
                new BigDecimal("0.9"), false, false,
                WeeklyMacdState.GREEN_EXPANDING, VolumeState.NORMAL,
                new BigDecimal("0.10"), false, VolumeState.NORMAL, false);
        CapitalContext capital = capital(new BigDecimal("1.0"), new BigDecimal("1.0"), new BigDecimal("100"));

        SignalResult result = service.evaluateSignal(fund, strategy(), market, capital, Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.SELL);
        assertThat(result.reason()).isEqualTo(SignalReason.LOGIC_BROKEN);
    }

    // ---- 定投止盈 ----

    @Test
    void 定投止盈_回撤达标_按浮盈收割比例计算份额() {
        FundEntity fund = fund(FundStatus.HOLDING);
        MarketIndicators market = marketWithCurrentNav(new BigDecimal("1.10"));
        CapitalContext capital = takeProfitCapital("20", "100");
        FundStrategyEntity strategy = strategy();
        strategy.setCyclePeakNav(new BigDecimal("1.20"));

        SignalResult result = service.evaluateSignal(fund, strategy, market, capital, Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.SELL);
        assertThat(result.triggerTier()).isNull();
        assertThat(result.reason()).isEqualTo(SignalReason.TRAILING_STOP);
        // 浮盈 20 × 收割 50% ÷ 净值 1.10 = 9.0909... 份。
        assertThat(result.suggestedMeasure().getValue()).isEqualByComparingTo(new BigDecimal("9.090909090909091"));
        assertThat(result.suggestedMeasure().getMeasureUnit()).isEqualTo(MeasureUnit.SHARE);
    }

    @Test
    void 定投止盈_单次最多卖当前持仓百分之二十() {
        FundEntity fund = fund(FundStatus.HOLDING);
        MarketIndicators market = marketWithCurrentNav(new BigDecimal("1.00"));
        CapitalContext capital = takeProfitCapital("100", "100");
        FundStrategyEntity strategy = strategy();
        strategy.setCyclePeakNav(new BigDecimal("1.10"));

        SignalResult result = service.evaluateSignal(fund, strategy, market, capital, Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.SELL);
        assertThat(result.suggestedMeasure().getValue()).isEqualByComparingTo(new BigDecimal("20"));
    }

    @Test
    void 定投止盈_成熟份额不足时只建议卖成熟份额() {
        FundEntity fund = fund(FundStatus.HOLDING);
        MarketIndicators market = marketWithCurrentNav(new BigDecimal("1.00"));
        CapitalContext capital = takeProfitCapital("100", "5");
        FundStrategyEntity strategy = strategy();
        strategy.setCyclePeakNav(new BigDecimal("1.10"));

        SignalResult result = service.evaluateSignal(fund, strategy, market, capital, Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.SELL);
        assertThat(result.suggestedMeasure().getValue()).isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    void 定投止盈_回撤未达阈值_不触发() {
        FundEntity fund = fund(FundStatus.HOLDING);
        MarketIndicators market = marketWithCurrentNav(new BigDecimal("1.15"));
        CapitalContext capital = takeProfitCapital("20", "100");
        FundStrategyEntity strategy = strategy();
        strategy.setCyclePeakNav(new BigDecimal("1.20"));

        SignalResult result = service.evaluateSignal(fund, strategy, market, capital, Instant.now(), 100);

        assertThat(result.signalType()).isNotEqualTo(SignalType.SELL);
        assertThat(result.reason()).isEqualTo(SignalReason.NO_SELL_TRIGGER);
    }

    @Test
    void SELL优先级_逻辑止损与移动止盈同时满足_返回逻辑止损() {
        FundEntity fund = fund(FundStatus.HOLDING);
        // 同时满足逻辑止损(破年线+MACD绿柱扩大+放量)和移动止盈(holdingPeak=1.2, current=0.80 → 回落 0.333 达四档)
        MarketIndicators market = new MarketIndicators(
                new BigDecimal("0.80"), false, false,
                WeeklyMacdState.GREEN_EXPANDING, VolumeState.NORMAL,
                BigDecimal.ZERO, false, VolumeState.HIGH_DROP, true);
        CapitalContext capital = capital(new BigDecimal("1.0"), new BigDecimal("1.2"), new BigDecimal("100"));

        SignalResult result = service.evaluateSignal(fund, strategy(), market, capital, Instant.now(), 100);

        assertThat(result.reason()).isEqualTo(SignalReason.LOGIC_BROKEN);
    }

    // ---- MIN_HOLD_DAYS ----

    @Test
    void 定投止盈_最近买入未满5交易日_成熟旧份额仍可卖() {
        FundEntity fund = fund(FundStatus.HOLDING);
        MarketIndicators market = marketWithCurrentNav(new BigDecimal("1.00"));
        CapitalContext capital = takeProfitCapital("100", "30");
        FundStrategyEntity strategy = strategy();
        strategy.setCyclePeakNav(new BigDecimal("1.10"));

        SignalResult result = service.evaluateSignal(fund, strategy, market, capital, Instant.now(), 2);

        assertThat(result.signalType()).isEqualTo(SignalType.SELL);
        assertThat(result.reason()).isEqualTo(SignalReason.TRAILING_STOP);
    }

    @Test
    void SELL逻辑止损_未满5交易日_豁免但记OVERRIDDEN() {
        FundEntity fund = fund(FundStatus.HOLDING);
        // 逻辑止损三条件全满足(破年线+MACD绿柱扩大+跟踪指数放量下跌);tradingDays=2 < 5 但豁免
        MarketIndicators market = new MarketIndicators(
                new BigDecimal("0.9"), false, false,
                WeeklyMacdState.GREEN_EXPANDING, VolumeState.NORMAL,
                BigDecimal.ZERO, false, VolumeState.HIGH_DROP, true);
        CapitalContext capital = capital(new BigDecimal("1.0"), new BigDecimal("1.0"), new BigDecimal("100"));

        SignalResult result = service.evaluateSignal(fund, strategy(), market, capital, Instant.now(), 2);

        assertThat(result.signalType()).isEqualTo(SignalType.SELL);
        assertThat(result.reason()).isEqualTo(SignalReason.LOGIC_BROKEN);
        assertThat(result.warnings()).contains(SignalWarningValue.of(SignalWarning.MIN_HOLD_DAYS_OVERRIDDEN));
    }

    // ---- fixtures ----

    private FundEntity fund(FundStatus status) {
        FundEntity fund = new FundEntity();
        fund.setStatus(status);
        fund.setFundCategory(FundCategory.BROAD_BASE);
        fund.setFundSubType(FundSubType.INDEX);
        return fund;
    }

    private FundStrategyEntity strategy() {
        FundStrategyEntity s = new FundStrategyEntity();
        s.setStatus(StrategyParamStatus.EFFECTIVE);
        s.setStopLossPullbackPercent(new BigDecimal("0.06"));
        s.setProfitHarvestPercent(new BigDecimal("0.50"));
        s.setMinimumHoldingPercent(new BigDecimal("0.50"));
        s.setMaxSingleSellPercent(new BigDecimal("0.20"));
        return s;
    }

    private MarketIndicators market() {
        return new MarketIndicators(
                new BigDecimal("1.0"), true, true,
                WeeklyMacdState.GREEN_SHRINKING, VolumeState.NORMAL,
                BigDecimal.ZERO, true, VolumeState.NORMAL, false);
    }

    private CapitalContext capital(BigDecimal peakNav, BigDecimal holdingPeriodPeakNav, BigDecimal holdingShares) {
        return new CapitalContext(peakNav, holdingPeriodPeakNav, holdingShares, Instant.now());
    }

    private CapitalContext capital() {
        return capital(new BigDecimal("1.0"), new BigDecimal("1.0"), new BigDecimal("100"));
    }

    private CapitalContext takeProfitCapital(String floatingProfit, String matureShares) {
        return new CapitalContext(
                new BigDecimal("1.2"),
                new BigDecimal("1.2"),
                new BigDecimal("100"),
                Instant.now(),
                new BigDecimal(floatingProfit),
                new BigDecimal(matureShares),
                true);
    }

    private MarketIndicators marketWithCurrentNav(BigDecimal currentNav) {
        // priceAboveYearLine/yearLineRising 默认 true 使逻辑止损不触发,专注移动止盈
        return new MarketIndicators(
                currentNav, true, true,
                WeeklyMacdState.GREEN_SHRINKING, VolumeState.NORMAL,
                BigDecimal.ZERO, false, VolumeState.NORMAL, false);
    }
}
