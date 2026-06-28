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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #12 信号引擎测试。循环 A:状态门控(CLEARED/PENDING_HOLDING/HOLDING × 无策略/有效策略)。
 * 循环 B/C/D 在本类追加 decideAction / warnings / hardConstraints / minHoldDays 测试。
 */
class DisciplineStrategyServiceTest {

    private final DisciplineStrategyService service = new DisciplineStrategyService();

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
        FundEntity fund = fund(FundStatus.PENDING_HOLDING);
        FundStrategyEntity strategy = strategy();
        strategy.setStatus(StrategyParamStatus.PENDING_CALIBRATION);

        SignalResult result = service.evaluateSignal(fund, strategy, market(), capital(), Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.NONE);
        assertThat(result.reason()).isEqualTo(SignalReason.NO_STRATEGY);
    }

    @Test
    void 状态PENDING_HOLDING_满足建仓三条件_返回BUILD() {
        FundEntity fund = fund(FundStatus.PENDING_HOLDING);
        MarketIndicators market = marketAboveYearLineRising60DayHigh();

        SignalResult result = service.evaluateSignal(fund, strategy(), market, capital(), Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.BUILD);
        assertThat(result.reason()).isEqualTo(SignalReason.BUILD);
        // 建仓额 = plannedTotalAmount(1000) × 0.10 = 100
        assertThat(result.suggestedMeasure().getValue()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(result.suggestedMeasure().getMeasureUnit()).isEqualTo(MeasureUnit.AMOUNT);
    }

    @Test
    void 状态PENDING_HOLDING_非60日新高_返回NONE_BUILD_CONDITION_NOT_MET() {
        FundEntity fund = fund(FundStatus.PENDING_HOLDING);
        MarketIndicators market = marketAboveYearLineRisingButNot60DayHigh();

        SignalResult result = service.evaluateSignal(fund, strategy(), market, capital(), Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.NONE);
        assertThat(result.reason()).isEqualTo(SignalReason.BUILD_CONDITION_NOT_MET);
    }

    @Test
    void 状态PENDING_HOLDING_年线向下_返回NONE_BUILD_CONDITION_NOT_MET() {
        FundEntity fund = fund(FundStatus.PENDING_HOLDING);
        MarketIndicators market = new MarketIndicators(
                new BigDecimal("1.0"), true, false, // 年线下行
                WeeklyMacdState.GREEN_SHRINKING, VolumeState.NORMAL,
                BigDecimal.ZERO, true, VolumeState.NORMAL, false);

        SignalResult result = service.evaluateSignal(fund, strategy(), market, capital(), Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.NONE);
        assertThat(result.reason()).isEqualTo(SignalReason.BUILD_CONDITION_NOT_MET);
    }

    @Test
    void 状态HOLDING_回撤跌破一档阈值_返回ADD_tier1() {
        FundEntity fund = fund(FundStatus.HOLDING);
        // peakNav=1.0, currentNav=0.91 → drawdown=-0.09 <= tier1Drawdown=-0.08;holdingPeak=0.91 使移动止盈回落=0 不触发
        CapitalContext capital = capitalWithPeakNavAndHoldingPeak(new BigDecimal("1.0"), new BigDecimal("0.91"),
                new BigDecimal("1000"), BigDecimal.ZERO, Map.of());
        MarketIndicators market = marketWithCurrentNav(new BigDecimal("0.91"));

        SignalResult result = service.evaluateSignal(fund, strategy(), market, capital, Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.ADD);
        assertThat(result.triggerTier()).isEqualTo(1);
        // 加仓额 = 1000 × 0.15 × 系数(1.0) = 150
        assertThat(result.suggestedMeasure().getValue()).isEqualByComparingTo(new BigDecimal("150"));
    }

    @Test
    void 状态HOLDING_回撤未跌到一档_返回NONE_NO_ADD_TIER() {
        FundEntity fund = fund(FundStatus.HOLDING);
        // peakNav=1.0, currentNav=0.95 → drawdown=-0.05 > tier1Drawdown=-0.08(未跌破)
        CapitalContext capital = capitalWithPeakNavAndHoldingPeak(new BigDecimal("1.0"), new BigDecimal("0.95"),
                new BigDecimal("1000"), BigDecimal.ZERO, Map.of());
        MarketIndicators market = marketWithCurrentNav(new BigDecimal("0.95"));

        SignalResult result = service.evaluateSignal(fund, strategy(), market, capital, Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.NONE);
        assertThat(result.reason()).isEqualTo(SignalReason.NO_ADD_TIER);
    }

    @Test
    void 状态HOLDING_一二档已触发_回撤跌破三档_返回ADD_tier3() {
        FundEntity fund = fund(FundStatus.HOLDING);
        FundStrategyEntity strategy = strategy();
        Instant past = Instant.now().minus(20, java.time.temporal.ChronoUnit.DAYS);
        strategy.setTier1AddedAt(past);
        strategy.setTier2AddedAt(past);
        // peakNav=1.0, currentNav=0.74 → drawdown=-0.26 <= tier3Drawdown=-0.25;holdingPeak=0.74 使移动止盈不触发;tier1/2 已触发跳过
        CapitalContext capital = capitalWithPeakNavAndHoldingPeak(new BigDecimal("1.0"), new BigDecimal("0.74"),
                new BigDecimal("1000"), BigDecimal.ZERO, Map.of());
        MarketIndicators market = marketWithCurrentNav(new BigDecimal("0.74"));

        SignalResult result = service.evaluateSignal(fund, strategy, market, capital, Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.ADD);
        assertThat(result.triggerTier()).isEqualTo(3);
        // 加仓额 = 1000 × 0.25 = 250
        assertThat(result.suggestedMeasure().getValue()).isEqualByComparingTo(new BigDecimal("250"));
    }

    @Test
    void 状态HOLDING_一档已触发_回撤跌破二档_返回ADD_tier2() {
        FundEntity fund = fund(FundStatus.HOLDING);
        FundStrategyEntity strategy = strategy();
        strategy.setTier1AddedAt(Instant.now().minus(10, java.time.temporal.ChronoUnit.DAYS));
        // peakNav=1.0, currentNav=0.84 → drawdown=-0.16 <= tier2Drawdown=-0.15, tier1 已触发跳过;holdingPeak=0.84 使移动止盈不触发
        CapitalContext capital = capitalWithPeakNavAndHoldingPeak(new BigDecimal("1.0"), new BigDecimal("0.84"),
                new BigDecimal("1000"), BigDecimal.ZERO, Map.of());
        MarketIndicators market = marketWithCurrentNav(new BigDecimal("0.84"));

        SignalResult result = service.evaluateSignal(fund, strategy, market, capital, Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.ADD);
        assertThat(result.triggerTier()).isEqualTo(2);
    }

    @Test
    void 反弹清空_回撤回升变浅_清空已加标记并记TIER_CLEARED警告() {
        FundEntity fund = fund(FundStatus.HOLDING);
        FundStrategyEntity strategy = strategy();
        strategy.setTier1AddedAt(Instant.now().minus(20, java.time.temporal.ChronoUnit.DAYS));
        strategy.setTier2AddedAt(Instant.now().minus(15, java.time.temporal.ChronoUnit.DAYS));
        // peakNav=1.0, currentNav=0.96 → drawdown=-0.04 > tier1Drawdown(-0.08)-0.005=-0.085,清空全部已加档;holdingPeak=0.96 使移动止盈不触发
        CapitalContext capital = capitalWithPeakNavAndHoldingPeak(new BigDecimal("1.0"), new BigDecimal("0.96"),
                new BigDecimal("1000"), BigDecimal.ZERO, Map.of());
        MarketIndicators market = marketWithCurrentNav(new BigDecimal("0.96"));

        SignalResult result = service.evaluateSignal(fund, strategy, market, capital, Instant.now(), 100);

        // 清空后无档位可加 → ADD 不触发;warnings 含 TIER_CLEARED
        assertThat(result.warnings()).anyMatch(w -> w.warning() == SignalWarning.TIER_CLEARED);
        assertThat(strategy.getTier1AddedAt()).isNull();
        assertThat(strategy.getTier2AddedAt()).isNull();
    }

    @Test
    void 反弹清空_回撤仍深_不清空已加标记() {
        FundEntity fund = fund(FundStatus.HOLDING);
        FundStrategyEntity strategy = strategy();
        Instant t1 = Instant.now().minus(20, java.time.temporal.ChronoUnit.DAYS);
        strategy.setTier1AddedAt(t1);
        // peakNav=1.0, currentNav=0.90 → drawdown=-0.10 <= tier1Drawdown=-0.08(仍深),不清空;holdingPeak=0.90 使移动止盈不触发;tier1 已触发跳过,tier2 未到
        CapitalContext capital = capitalWithPeakNavAndHoldingPeak(new BigDecimal("1.0"), new BigDecimal("0.90"),
                new BigDecimal("1000"), BigDecimal.ZERO, Map.of());
        MarketIndicators market = marketWithCurrentNav(new BigDecimal("0.90"));

        SignalResult result = service.evaluateSignal(fund, strategy, market, capital, Instant.now(), 100);

        assertThat(strategy.getTier1AddedAt()).isEqualTo(t1);
        assertThat(result.warnings()).noneMatch(w -> w.warning() == SignalWarning.TIER_CLEARED);
    }

    // ---- 循环 C:SELL 三分支 ----

    @Test
    void 逻辑止损_ETF三条件全满足_返回SELL_LOGIC_BROKEN() {
        FundEntity fund = fund(FundStatus.HOLDING);
        // 破年线 + MACD绿柱扩大 + 跟踪指数放量下跌
        MarketIndicators market = new MarketIndicators(
                new BigDecimal("0.9"), false, false,
                WeeklyMacdState.GREEN_EXPANDING, VolumeState.NORMAL,
                BigDecimal.ZERO, false, VolumeState.HIGH_DROP, true);
        CapitalContext capital = capitalWithHoldingShares(new BigDecimal("100"));

        SignalResult result = service.evaluateSignal(fund, strategy(), market, capital, Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.SELL);
        assertThat(result.reason()).isEqualTo(SignalReason.LOGIC_BROKEN);
        assertThat(result.suggestedMeasure().getValue()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(result.suggestedMeasure().getMeasureUnit()).isEqualTo(MeasureUnit.SHARE);
    }

    @Test
    void 逻辑止损_ETF仅破年线未放量_不触发() {
        FundEntity fund = fund(FundStatus.HOLDING);
        MarketIndicators market = new MarketIndicators(
                new BigDecimal("0.9"), false, false,
                WeeklyMacdState.GREEN_EXPANDING, VolumeState.NORMAL,
                BigDecimal.ZERO, false, VolumeState.NORMAL, true); // 跟踪指数未放量
        CapitalContext capital = capitalWithHoldingShares(new BigDecimal("100"));

        SignalResult result = service.evaluateSignal(fund, strategy(), market, capital, Instant.now(), 100);

        assertThat(result.signalType()).isNotEqualTo(SignalType.SELL);
    }

    @Test
    void 逻辑止损_主动基金三条件满足_返回SELL_LOGIC_BROKEN() {
        FundEntity fund = fund(FundStatus.HOLDING);
        fund.setFundSubType(FundSubType.ACTIVE);
        // 破年线 + MACD绿柱扩大 + 单周跌幅0.10 > 阈值0.08
        MarketIndicators market = new MarketIndicators(
                new BigDecimal("0.9"), false, false,
                WeeklyMacdState.GREEN_EXPANDING, VolumeState.NORMAL,
                new BigDecimal("0.10"), false, VolumeState.NORMAL, false);
        CapitalContext capital = capitalWithHoldingShares(new BigDecimal("100"));

        SignalResult result = service.evaluateSignal(fund, strategy(), market, capital, Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.SELL);
        assertThat(result.reason()).isEqualTo(SignalReason.LOGIC_BROKEN);
    }

    @Test
    void 移动止盈_回落达四档阈值_卖第四档连建仓份额() {
        FundEntity fund = fund(FundStatus.HOLDING);
        FundStrategyEntity strategy = strategy();
        Instant past = Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);
        strategy.setTier4AddedAt(past);
        // peakNav=1.3(前高), holdingPeak=1.2(持有期高点), currentNav=0.80
        // 前高回撤=(0.8-1.3)/1.3=-0.385 <= tier4Drawdown(-0.35) 维持四档不清;持有期高点回落=(1.2-0.8)/1.2=0.333 >= 4×0.08=0.32 达四档止盈
        MarketIndicators market = marketWithCurrentNav(new BigDecimal("0.80"));
        CapitalContext capital = capitalWithPeakNavAndHoldingPeak(new BigDecimal("1.3"), new BigDecimal("1.2"),
                new BigDecimal("1000"), new BigDecimal("50"), Map.of(4, new BigDecimal("30")));

        SignalResult result = service.evaluateSignal(fund, strategy, market, capital, Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.SELL);
        assertThat(result.triggerTier()).isEqualTo(4);
        assertThat(result.reason()).isEqualTo(SignalReason.TRAILING_STOP);
        // 第四档连卖:tier4 加仓份额 30 + buildShares 50 = 80
        assertThat(result.suggestedMeasure().getValue()).isEqualByComparingTo(new BigDecimal("80"));
    }

    @Test
    void 移动止盈_回落达二档_卖第二档份额() {
        FundEntity fund = fund(FundStatus.HOLDING);
        FundStrategyEntity strategy = strategy();
        Instant past = Instant.now().minus(20, java.time.temporal.ChronoUnit.DAYS);
        strategy.setTier1AddedAt(past);
        strategy.setTier2AddedAt(past);
        // holdingPeriodPeakNav=1.0, currentNav=0.82 → 回落 0.18 >= 2×0.08=0.16 达二档
        MarketIndicators market = marketWithCurrentNav(new BigDecimal("0.82"));
        CapitalContext capital = capitalWithPeakNavAndHoldingPeak(new BigDecimal("1.0"), new BigDecimal("1.0"),
                new BigDecimal("1000"), BigDecimal.ZERO, Map.of(2, new BigDecimal("20")));

        SignalResult result = service.evaluateSignal(fund, strategy, market, capital, Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.SELL);
        assertThat(result.triggerTier()).isEqualTo(2);
        assertThat(result.suggestedMeasure().getValue()).isEqualByComparingTo(new BigDecimal("20"));
    }

    @Test
    void 移动止盈_空档轮空_跳过null档找更浅档() {
        FundEntity fund = fund(FundStatus.HOLDING);
        FundStrategyEntity strategy = strategy();
        Instant past = Instant.now().minus(20, java.time.temporal.ChronoUnit.DAYS);
        // 四档应触发但 tier4AddedAt=null;tier3 也 null;tier2 设了 → 卖 tier2
        strategy.setTier2AddedAt(past);
        // holdingPeriodPeakNav=1.0, currentNav=0.68 → 回落 0.32 >= 4×0.08=0.32 达四档;但 tier4/tier3 空,轮空到 tier2
        MarketIndicators market = marketWithCurrentNav(new BigDecimal("0.68"));
        CapitalContext capital = capitalWithPeakNavAndHoldingPeak(new BigDecimal("1.0"), new BigDecimal("1.0"),
                new BigDecimal("1000"), BigDecimal.ZERO, Map.of(2, new BigDecimal("20")));

        SignalResult result = service.evaluateSignal(fund, strategy, market, capital, Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.SELL);
        assertThat(result.triggerTier()).isEqualTo(2);
    }

    @Test
    void 移动止盈_应触发档及更浅档全为null_返回NONE_NO_TIER_TO_SELL() {
        FundEntity fund = fund(FundStatus.HOLDING);
        FundStrategyEntity strategy = strategy(); // 无任何 tierNAddedAt
        // 回落达一档但 tier1 也 null
        MarketIndicators market = marketWithCurrentNav(new BigDecimal("0.90")); // 回落 0.10 >= 0.08
        CapitalContext capital = capitalWithPeakNavAndHoldingPeak(new BigDecimal("1.0"), new BigDecimal("1.0"),
                new BigDecimal("1000"), BigDecimal.ZERO, Map.of());

        SignalResult result = service.evaluateSignal(fund, strategy, market, capital, Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.NONE);
        assertThat(result.reason()).isEqualTo(SignalReason.NO_TIER_TO_SELL);
    }

    @Test
    void 移动止盈_回落未达一档阈值_不触发() {
        FundEntity fund = fund(FundStatus.HOLDING);
        FundStrategyEntity strategy = strategy();
        strategy.setTier1AddedAt(Instant.now().minus(10, java.time.temporal.ChronoUnit.DAYS));
        // holdingPeriodPeakNav=1.0, currentNav=0.95 → 回落 0.05 < 0.08 未达一档
        MarketIndicators market = marketWithCurrentNav(new BigDecimal("0.95"));
        CapitalContext capital = capitalWithPeakNavAndHoldingPeak(new BigDecimal("1.0"), new BigDecimal("1.0"),
                new BigDecimal("1000"), BigDecimal.ZERO, Map.of(1, new BigDecimal("15")));

        SignalResult result = service.evaluateSignal(fund, strategy, market, capital, Instant.now(), 100);

        assertThat(result.signalType()).isNotEqualTo(SignalType.SELL);
    }

    @Test
    void 再平衡_单只占比超限_返回SELL_REBALANCE() {
        FundEntity fund = fund(FundStatus.HOLDING);
        fund.setFundCategory(FundCategory.BROAD_BASE);
        MarketIndicators market = marketWithCurrentNav(new BigDecimal("1.0"));
        // singlePositionPct=0.35 > 0.30(单只上限无关类型);totalEquityAmount=10000;卖出金额=(0.35-0.30)×10000=500;份额=500/1.0=500
        CapitalContext capital = capitalWithPosition(new BigDecimal("0.35"), new BigDecimal("10000"));

        SignalResult result = service.evaluateSignal(fund, strategy(), market, capital, Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.SELL);
        assertThat(result.reason()).isEqualTo(SignalReason.REBALANCE);
        assertThat(result.suggestedMeasure().getValue()).isEqualByComparingTo(new BigDecimal("500"));
    }

    @Test
    void 再平衡_行业基金占比15percent未超限_不触发() {
        FundEntity fund = fund(FundStatus.HOLDING);
        fund.setFundCategory(FundCategory.SECTOR); // 单只上限 30%(无关类型)
        MarketIndicators market = marketWithCurrentNav(new BigDecimal("1.0"));
        CapitalContext capital = capitalWithPosition(new BigDecimal("0.15"), new BigDecimal("10000"));

        SignalResult result = service.evaluateSignal(fund, strategy(), market, capital, Instant.now(), 100);

        // 占比 0.15 < 上限 0.30,未超限不触发 SELL
        assertThat(result.signalType()).as("reason=%s", result.reason()).isNotEqualTo(SignalType.SELL);
    }

    @Test
    void SELL优先级_逻辑止损与移动止盈同时满足_返回逻辑止损() {
        FundEntity fund = fund(FundStatus.HOLDING);
        FundStrategyEntity strategy = strategy();
        strategy.setTier4AddedAt(Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS));
        // 同时满足逻辑止损(破年线+MACD绿柱扩大+放量)和移动止盈(回落达四档)
        MarketIndicators market = new MarketIndicators(
                new BigDecimal("0.80"), false, false,
                WeeklyMacdState.GREEN_EXPANDING, VolumeState.NORMAL,
                BigDecimal.ZERO, false, VolumeState.HIGH_DROP, true);
        CapitalContext capital = capitalWithPeakNavAndHoldingPeak(new BigDecimal("1.0"), new BigDecimal("1.2"),
                new BigDecimal("1000"), new BigDecimal("50"), Map.of(4, new BigDecimal("30")));

        SignalResult result = service.evaluateSignal(fund, strategy, market, capital, Instant.now(), 100);

        assertThat(result.reason()).isEqualTo(SignalReason.LOGIC_BROKEN);
    }

    // ---- 循环 D: warnings + hardConstraints + minHoldDays ----

    @Test
    void ADD信号_单周跌幅超阈值_加WEEKLY_COOLDOWN警告() {
        FundEntity fund = fund(FundStatus.HOLDING);
        // peakNav=1.0, currentNav=0.91 → drawdown=-0.09 <= tier1Drawdown=-0.08 触发 ADD tier1
        // weeklyDropPercent=0.10 > 阈值0.08 → WEEKLY_COOLDOWN
        MarketIndicators market = new MarketIndicators(
                new BigDecimal("0.91"), true, true,
                WeeklyMacdState.GREEN_SHRINKING, VolumeState.NORMAL,
                new BigDecimal("0.10"), false, VolumeState.NORMAL, false);
        CapitalContext capital = capitalWithPeakNavAndHoldingPeak(new BigDecimal("1.0"), new BigDecimal("0.91"),
                new BigDecimal("1000"), BigDecimal.ZERO, Map.of());

        SignalResult result = service.evaluateSignal(fund, strategy(), market, capital, Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.ADD);
        assertThat(result.warnings()).contains(SignalWarningValue.of(SignalWarning.WEEKLY_COOLDOWN));
    }

    @Test
    void ADD信号_年线下行且放量下跌_加BREAKDOWN_WATCH警告() {
        FundEntity fund = fund(FundStatus.HOLDING);
        // priceAboveYearLine=false, yearLineRising=false, volumeState=HIGH_DROP → BREAKDOWN_WATCH
        MarketIndicators market = new MarketIndicators(
                new BigDecimal("0.91"), false, false,
                WeeklyMacdState.GREEN_EXPANDING, com.fundpilot.backend.market.enums.VolumeState.HIGH_DROP,
                BigDecimal.ZERO, false, VolumeState.NORMAL, false);
        CapitalContext capital = capitalWithPeakNavAndHoldingPeak(new BigDecimal("1.0"), new BigDecimal("0.91"),
                new BigDecimal("1000"), BigDecimal.ZERO, Map.of());

        SignalResult result = service.evaluateSignal(fund, strategy(), market, capital, Instant.now(), 100);

        assertThat(result.warnings()).contains(SignalWarningValue.of(SignalWarning.BREAKDOWN_WATCH));
    }

    @Test
    void ADD信号_单只占比超硬约束上限_降级NONE_HARD_CONSTRAINT_BREACH() {
        FundEntity fund = fund(FundStatus.HOLDING);
        fund.setFundCategory(FundCategory.BROAD_BASE); // 单只上限 30%(无关类型)
        // ADD tier1: ratio=0.15, singleAddRatio=0.15 <= 0.50 OK;但 singlePositionPct=0.35 > 0.30 违反
        MarketIndicators market = new MarketIndicators(
                new BigDecimal("0.91"), true, true,
                WeeklyMacdState.GREEN_SHRINKING, VolumeState.NORMAL,
                BigDecimal.ZERO, false, VolumeState.NORMAL, false);
        CapitalContext capital = new CapitalContext(
                new BigDecimal("1.0"), new BigDecimal("0.91"),
                new BigDecimal("0.35"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("1000"), BigDecimal.ZERO, Map.of(), new BigDecimal("100"), Instant.now());

        SignalResult result = service.evaluateSignal(fund, strategy(), market, capital, Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.NONE);
        assertThat(result.reason()).isEqualTo(SignalReason.HARD_CONSTRAINT_BREACH);
        assertThat(result.hardConstraintBreaches()).anyMatch(b -> "SINGLE_POSITION_LIMIT".equals(b.name()));
    }

    @Test
    void ADD信号_占比未超限_通过硬约束保持ADD() {
        FundEntity fund = fund(FundStatus.HOLDING);
        fund.setFundCategory(FundCategory.BROAD_BASE);
        MarketIndicators market = new MarketIndicators(
                new BigDecimal("0.91"), true, true,
                WeeklyMacdState.GREEN_SHRINKING, VolumeState.NORMAL,
                BigDecimal.ZERO, false, VolumeState.NORMAL, false);
        CapitalContext capital = new CapitalContext(
                new BigDecimal("1.0"), new BigDecimal("0.91"),
                new BigDecimal("0.10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("1000"), BigDecimal.ZERO, Map.of(), new BigDecimal("100"), Instant.now());

        SignalResult result = service.evaluateSignal(fund, strategy(), market, capital, Instant.now(), 100);

        assertThat(result.signalType()).isEqualTo(SignalType.ADD);
        assertThat(result.hardConstraintBreaches()).isEmpty();
    }

    @Test
    void SELL移动止盈_未满5交易日_降级NONE_MIN_HOLD_DAYS_NOT_MET() {
        FundEntity fund = fund(FundStatus.HOLDING);
        FundStrategyEntity strategy = strategy();
        strategy.setTier1AddedAt(Instant.now().minus(2, java.time.temporal.ChronoUnit.DAYS));
        // holdingPeak=1.0, currentNav=0.90 → 回落 0.10 >= 0.08 达一档止盈;但 tradingDays=2 < 5
        MarketIndicators market = marketWithCurrentNav(new BigDecimal("0.90"));
        CapitalContext capital = capitalWithPeakNavAndHoldingPeak(new BigDecimal("1.0"), new BigDecimal("1.0"),
                new BigDecimal("1000"), BigDecimal.ZERO, Map.of(1, new BigDecimal("15")));

        SignalResult result = service.evaluateSignal(fund, strategy, market, capital, Instant.now(), 2);

        assertThat(result.signalType()).isEqualTo(SignalType.NONE);
        assertThat(result.reason()).isEqualTo(SignalReason.MIN_HOLD_DAYS_NOT_MET);
    }

    @Test
    void SELL逻辑止损_未满5交易日_豁免但记OVERRIDDEN() {
        FundEntity fund = fund(FundStatus.HOLDING);
        // 逻辑止损三条件全满足(破年线+MACD绿柱扩大+跟踪指数放量下跌);tradingDays=2 < 5 但豁免
        MarketIndicators market = new MarketIndicators(
                new BigDecimal("0.9"), false, false,
                WeeklyMacdState.GREEN_EXPANDING, VolumeState.NORMAL,
                BigDecimal.ZERO, false, VolumeState.HIGH_DROP, true);
        CapitalContext capital = capitalWithHoldingShares(new BigDecimal("100"));

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
        s.setTier1Drawdown(new BigDecimal("-0.08"));
        s.setTier2Drawdown(new BigDecimal("-0.15"));
        s.setTier3Drawdown(new BigDecimal("-0.25"));
        s.setTier4Drawdown(new BigDecimal("-0.35"));
        s.setTier1Ratio(new BigDecimal("0.15"));
        s.setTier2Ratio(new BigDecimal("0.20"));
        s.setTier3Ratio(new BigDecimal("0.25"));
        s.setTier4Ratio(new BigDecimal("0.30"));
        s.setWeeklyCoolDownThreshold(new BigDecimal("0.08"));
        s.setStopLossPullbackPercent(new BigDecimal("0.08"));
        return s;
    }

    private MarketIndicators market() {
        return new MarketIndicators(
                new BigDecimal("1.0"), true, true,
                WeeklyMacdState.GREEN_SHRINKING, VolumeState.NORMAL,
                BigDecimal.ZERO, true, VolumeState.NORMAL, false);
    }

    private CapitalContext capital() {
        return new CapitalContext(
                new BigDecimal("1.0"), new BigDecimal("1.0"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("1000"), BigDecimal.ZERO, Map.of(), BigDecimal.ZERO, Instant.now());
    }

    private CapitalContext capitalWithPeakNav(BigDecimal peakNav, BigDecimal plannedTotal) {
        return new CapitalContext(
                peakNav, peakNav,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                plannedTotal, BigDecimal.ZERO, Map.of(), BigDecimal.ZERO, Instant.now());
    }

    private MarketIndicators marketAboveYearLineRising60DayHigh() {
        return new MarketIndicators(
                new BigDecimal("1.0"), true, true,
                WeeklyMacdState.GREEN_SHRINKING, VolumeState.NORMAL,
                BigDecimal.ZERO, true, VolumeState.NORMAL, false);
    }

    private MarketIndicators marketAboveYearLineRisingButNot60DayHigh() {
        return new MarketIndicators(
                new BigDecimal("1.0"), true, true,
                WeeklyMacdState.GREEN_SHRINKING, VolumeState.NORMAL,
                BigDecimal.ZERO, false, VolumeState.NORMAL, false);
    }

    private MarketIndicators marketWithCurrentNav(BigDecimal currentNav) {
        // HOLDING 状态不看 60 日新高(那是 BUILD 条件),这里默认 false;priceAboveYearLine/yearLineRising 默认 true
        return new MarketIndicators(
                currentNav, true, true,
                WeeklyMacdState.GREEN_SHRINKING, VolumeState.NORMAL,
                BigDecimal.ZERO, false, VolumeState.NORMAL, false);
    }

    private CapitalContext capitalWithHoldingShares(BigDecimal holdingShares) {
        return new CapitalContext(
                new BigDecimal("1.0"), new BigDecimal("1.0"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("1000"), BigDecimal.ZERO, Map.of(), holdingShares, Instant.now());
    }

    private CapitalContext capitalWithPeakNavAndHoldingPeak(BigDecimal peakNav, BigDecimal holdingPeak,
                                                             BigDecimal plannedTotal, BigDecimal buildShares,
                                                             Map<Integer, BigDecimal> tierAddShares) {
        return new CapitalContext(
                peakNav, holdingPeak,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                plannedTotal, buildShares, tierAddShares, new BigDecimal("100"), Instant.now());
    }

    private CapitalContext capitalWithPosition(BigDecimal singlePositionPct, BigDecimal totalEquityAmount) {
        return new CapitalContext(
                new BigDecimal("1.0"), new BigDecimal("1.0"),
                singlePositionPct, BigDecimal.ZERO, BigDecimal.ZERO, totalEquityAmount,
                new BigDecimal("1000"), BigDecimal.ZERO, Map.of(), new BigDecimal("100"), Instant.now());
    }
}
