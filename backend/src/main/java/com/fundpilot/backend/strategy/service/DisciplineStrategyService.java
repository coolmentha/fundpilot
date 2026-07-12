package com.fundpilot.backend.strategy.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import com.fundpilot.backend.signal.enums.MeasureUnit;
import com.fundpilot.backend.signal.enums.SignalReason;
import com.fundpilot.backend.signal.enums.SignalType;
import com.fundpilot.backend.signal.enums.SignalWarning;
import com.fundpilot.backend.signal.enums.SignalWarningValue;
import com.fundpilot.backend.signal.valueobject.Measure;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import com.fundpilot.backend.strategy.service.support.CapitalContext;
import com.fundpilot.backend.strategy.service.support.MarketIndicators;
import com.fundpilot.backend.strategy.service.support.SignalResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 信号引擎(卖出纪律专用):纯函数 {@code evaluateSignal} 对单只基金产出卖出建议。
 * <p>零 Spring/DB 依赖——所有外部值(峰值、份额、行情指标)由调用方预注入,
 * 便于单测构造数值即可覆盖各分支。SignalGenerationService 负责取数后调用本类。
 *
 * <h3>流程(金字塔退场后简化)</h3>
 * <ol>
 *   <li>状态门控:CLEARED→NONE;PENDING_HOLDING→NONE(不再给建仓建议,买入由用户手动/定投决定)</li>
 *   <li>策略生效:status≠EFFECTIVE→NONE(NO_STRATEGY)</li>
 *   <li>SELL 决策:逻辑止损(优先级最高)> 定投止盈(盈利启动后按周期高点回撤)</li>
 *   <li>最近买入未满 5 交易日时,逻辑止损记录豁免告警;定投止盈按 lot 保护新份额</li>
 *   <li>组装 SignalResult</li>
 * </ol>
 *
 * <p>金字塔加仓机制(建仓/四档加仓/计划总仓位/反弹清空/BUILD-ADD 硬约束)已随行情工作台转向移除。
 * 定投止盈以整仓盈利为启动条件,同一周期只触发一次并保留长期底仓。
 *
 * <p>回撤约定:drawdown = (currentNav - peakNav) / peakNav,负数表示跌幅。
 */
@Service
public class DisciplineStrategyService {

    private static final MathContext MATH = MathContext.DECIMAL64;
    /** 7 天内不赎回硬约束窗口(交易日)。 */
    private static final int MIN_HOLD_DAYS = 5;

    /**
     * @param fund                      基金(含 status/fundCategory/fundSubType 等)
     * @param strategy                  生效策略版本;可为 null(无策略)
     * @param market                    行情指标快照
     * @param capital                   资金与仓位上下文(4 字段:峰值/持有期高点/持仓份额/最近买入时间)
     * @param today                     信号生成日(14:50)
     * @param tradingDaysSinceLastBuy   最近一次买入确认至今的交易日数(MIN_HOLD_DAYS 判定,由调用方用 TradingCalendarService 预算)
     * @return 信号结果(NONE/SELL + reason/warnings)
     */
    public SignalResult evaluateSignal(FundEntity fund, FundStrategyEntity strategy,
                                       MarketIndicators market, CapitalContext capital, Instant today,
                                       long tradingDaysSinceLastBuy) {
        // 步骤 1:状态门控
        FundStatus status = fund.getStatus();
        if (status == FundStatus.CLEARED) {
            return SignalResult.none(SignalReason.FUND_CLEARED);
        }
        // 步骤 2:策略生效
        if (strategy == null || strategy.getStatus() != StrategyParamStatus.EFFECTIVE) {
            return SignalResult.none(SignalReason.NO_STRATEGY);
        }
        // PENDING_HOLDING 不再给建仓建议——买入由用户手动/定投决定
        if (status == FundStatus.PENDING_HOLDING) {
            return SignalResult.none(SignalReason.NO_STRATEGY);
        }

        List<SignalWarningValue> warnings = new ArrayList<>();

        // 步骤 3:SELL 决策(逻辑止损 > 移动止盈)
        SignalResult result = decideSell(fund, strategy, market, capital, warnings);

        // 步骤 4:逻辑止损在最近买入未满 5 交易日时记录豁免告警。
        if (result.signalType() == SignalType.SELL) {
            result = applyMinHoldDays(result, tradingDaysSinceLastBuy, warnings);
        }

        // 步骤 5:组装(重建 SignalResult 以携带完整 warnings)
        return new SignalResult(result.signalType(), result.triggerTier(), result.coefficient(),
                result.suggestedMeasure(), result.reason(), List.copyOf(warnings), result.hardConstraintBreaches());
    }

    /**
     * SELL 决策:逻辑止损(优先级最高,豁免 MIN_HOLD_DAYS)> 移动止盈。命中即返回,否则 NONE。
     * <p>SELL 信号最多一类(CONTEXT.md「SELL 信号优先级」)。
     */
    private SignalResult decideSell(FundEntity fund, FundStrategyEntity strategy,
                                    MarketIndicators market, CapitalContext capital,
                                    List<SignalWarningValue> warnings) {
        // 1. 逻辑止损(优先级最高,豁免 MIN_HOLD_DAYS)
        SignalResult logicBroken = checkLogicBrokenStopLoss(fund, strategy, market, capital, warnings);
        if (logicBroken != null) {
            return logicBroken;
        }
        // 2. 定投止盈(盈利启动后按周期高点回撤)
        SignalResult trailingStop = checkTrailingStop(fund, strategy, market, capital, warnings);
        if (trailingStop != null) {
            return trailingStop;
        }
        return new SignalResult(SignalType.NONE, null, null, null, SignalReason.NO_SELL_TRIGGER, warnings, List.of());
    }

    /**
     * 逻辑止损:趋势死亡型,一次清空。按 fundSubType 分派:
     * <ul>
     *   <li>ETF/INDEX/INDEX_ENHANCED:破年线 + MACD绿柱扩大 + 跟踪指数放量下跌(当日量>20日均量×1.5 且 当日收跌)</li>
     *   <li>ACTIVE:破年线 + MACD绿柱扩大(原第三条件"单周跌幅>weeklyCoolDownThreshold"随金字塔移除;
     *       主动基金无量能数据,破年线+MACD绿柱扩大已足够表达趋势死亡)</li>
     * </ul>
     * 触发后 reason=LOGIC_BROKEN。MIN_HOLD_DAYS 豁免。
     */
    private SignalResult checkLogicBrokenStopLoss(FundEntity fund, FundStrategyEntity strategy,
                                                  MarketIndicators market,
                                                  CapitalContext capital, List<SignalWarningValue> warnings) {
        // 条件①:净值跌破年线(!priceAboveYearLine)
        if (market.priceAboveYearLine()) {
            return null;
        }
        // 条件②:周 MACD 绿柱扩大
        if (market.weeklyMacdState() != com.fundpilot.backend.market.enums.WeeklyMacdState.GREEN_EXPANDING) {
            return null;
        }
        // 条件③:按 fundSubType 分派
        com.fundpilot.backend.fund.enums.FundSubType subType = fund.getFundSubType();
        boolean condition3;
        if (subType == com.fundpilot.backend.fund.enums.FundSubType.ACTIVE) {
            // 主动基金:破年线+MACD绿柱扩大即触发(原 weeklyCoolDown 条件随金字塔移除)
            condition3 = true;
        } else {
            // ETF/INDEX/INDEX_ENHANCED:跟踪指数放量下跌
            condition3 = market.benchmarkVolumeState() == com.fundpilot.backend.market.enums.VolumeState.HIGH_DROP
                    && market.benchmarkDroppedToday();
        }
        if (!condition3) {
            return null;
        }
        // 触发:一次清空(全卖 holdingShares)
        BigDecimal shares = capital.holdingShares() != null ? capital.holdingShares() : BigDecimal.ZERO;
        Measure measure = new Measure(shares, MeasureUnit.SHARE);
        return new SignalResult(SignalType.SELL, null, null, measure, SignalReason.LOGIC_BROKEN, warnings, List.of());
    }

    /**
     * 定投止盈:整仓收益达到启动线后，从止盈周期高点回撤达到阈值，收割部分浮盈。
     * 卖出份额受浮盈收割、单次上限、成熟 lot 和最低保留仓位共同限制。
     */
    private SignalResult checkTrailingStop(FundEntity fund, FundStrategyEntity strategy,
                                           MarketIndicators market, CapitalContext capital,
                                           List<SignalWarningValue> warnings) {
        if (!capital.takeProfitEvaluationEnabled()) {
            return null;
        }
        BigDecimal peak = strategy.getCyclePeakNav();
        BigDecimal currentAccumulatedNav = capital.currentAccumulatedNav();
        BigDecimal currentUnitNav = capital.currentUnitNav();
        if (peak == null || peak.signum() <= 0 || currentAccumulatedNav == null
                || currentUnitNav == null || currentUnitNav.signum() <= 0) {
            return null;
        }
        // 回落幅度 = (peak - current) / peak,正数
        BigDecimal pullback = peak.subtract(currentAccumulatedNav).divide(peak, MATH);
        BigDecimal stopLossPercent = strategy.getStopLossPullbackPercent();
        if (stopLossPercent == null || stopLossPercent.signum() == 0) {
            return null;
        }
        if (pullback.compareTo(stopLossPercent) < 0) {
            return null;
        }
        BigDecimal holdingShares = capital.holdingShares() != null ? capital.holdingShares() : BigDecimal.ZERO;
        BigDecimal currentValue = currentUnitNav.multiply(holdingShares, MATH);
        BigDecimal profitHarvestShares = capital.floatingProfit()
                .multiply(strategy.getProfitHarvestPercent(), MATH)
                .divide(currentUnitNav, MATH);
        BigDecimal singleSellCapShares = holdingShares.multiply(strategy.getMaxSingleSellPercent(), MATH);
        BigDecimal retentionCapShares = holdingShares.multiply(
                BigDecimal.ONE.subtract(strategy.getMinimumHoldingPercent()), MATH);
        BigDecimal matureShares = capital.matureRedeemableShares() != null
                ? capital.matureRedeemableShares() : BigDecimal.ZERO;
        BigDecimal shares = min(profitHarvestShares, singleSellCapShares, retentionCapShares, matureShares);
        if (shares.signum() <= 0 || currentValue.signum() <= 0) {
            return null;
        }
        Measure measure = new Measure(shares, MeasureUnit.SHARE);
        return new SignalResult(SignalType.SELL, null, null, measure, SignalReason.TRAILING_STOP, warnings, List.of());
    }

    /**
     * 逻辑止损在最近买入未满 5 交易日时仍执行，并记录 MIN_HOLD_DAYS_OVERRIDDEN。
     * 定投止盈的持有期保护已下沉到逐 lot 成熟份额计算。
     */
    private static SignalResult applyMinHoldDays(SignalResult result, long tradingDaysSinceLastBuy,
                                                 List<SignalWarningValue> warnings) {
        boolean logicBroken = result.reason() == SignalReason.LOGIC_BROKEN;
        if (logicBroken && tradingDaysSinceLastBuy < MIN_HOLD_DAYS) {
            warnings.add(SignalWarningValue.of(SignalWarning.MIN_HOLD_DAYS_OVERRIDDEN));
        }
        return result;
    }

    private static BigDecimal min(BigDecimal first, BigDecimal... rest) {
        BigDecimal result = first;
        for (BigDecimal value : rest) {
            result = result.min(value);
        }
        return result;
    }
}
