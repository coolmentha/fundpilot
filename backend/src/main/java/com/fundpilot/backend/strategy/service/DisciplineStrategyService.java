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
 *   <li>SELL 决策:逻辑止损(优先级最高,豁免 MIN_HOLD_DAYS)> 移动止盈(按回落分档减仓)</li>
 *   <li>MIN_HOLD_DAYS:移动止盈未满 5 交易日→NONE;逻辑止损豁免</li>
 *   <li>组装 SignalResult</li>
 * </ol>
 *
 * <p>金字塔加仓机制(建仓/四档加仓/计划总仓位/反弹清空/BUILD-ADD 硬约束)已随行情工作台转向移除。
 * 移动止盈从金字塔档位状态解耦为独立"按回落分档减仓":回落 n×阈值卖 holdingShares×(n/4)。
 *
 * <p>回撤约定:drawdown = (currentNav - peakNav) / peakNav,负数表示跌幅。
 */
@Service
public class DisciplineStrategyService {

    private static final MathContext MATH = MathContext.DECIMAL64;
    /** 移动止盈分档数:回落 1×阈值卖 1/4,4×阈值全卖。 */
    private static final int TRAILING_STOP_TIERS = 4;
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

        // 步骤 4:MIN_HOLD_DAYS(移动止盈未满 5 交易日→降级 NONE;逻辑止损豁免)
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
        // 2. 移动止盈(按回落分档减仓)
        SignalResult trailingStop = checkTrailingStop(fund, strategy, market, capital, warnings);
        if (trailingStop != null) {
            return trailingStop;
        }
        return new SignalResult(SignalType.NONE, null, null, null, SignalReason.NO_STRATEGY, warnings, List.of());
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
     * 移动止盈:从 holdingPeriodPeakNav 回落 n×stopLossPullbackPercent 触发卖 holdingShares×(n/4)。
     * <p>分档减仓:回落 1×阈值卖 1/4,2×阈值卖 1/2,3×阈值卖 3/4,4×阈值全卖。
     * 不依赖金字塔档位状态(已解耦)——份额基数是当前持仓 holdingShares,非各档加仓份额。
     */
    private SignalResult checkTrailingStop(FundEntity fund, FundStrategyEntity strategy,
                                           MarketIndicators market, CapitalContext capital,
                                           List<SignalWarningValue> warnings) {
        BigDecimal peak = capital.holdingPeriodPeakNav();
        BigDecimal currentNav = market.currentNav();
        if (peak == null || peak.signum() <= 0 || currentNav == null) {
            return null;
        }
        // 回落幅度 = (peak - current) / peak,正数
        BigDecimal pullback = peak.subtract(currentNav).divide(peak, MATH);
        BigDecimal stopLossPercent = strategy.getStopLossPullbackPercent();
        if (stopLossPercent == null || stopLossPercent.signum() == 0) {
            return null;
        }
        // stopLossPullbackPercent 约定为负数(如 -0.08 表回落 8%),取绝对值参与计算
        BigDecimal threshold = stopLossPercent.abs();
        // 计算应触发的档位:pullback >= n × threshold,n 从 4 降到 1,取最大 n
        int triggerTier = 0;
        for (int n = TRAILING_STOP_TIERS; n >= 1; n--) {
            if (pullback.compareTo(threshold.multiply(BigDecimal.valueOf(n), MATH)) >= 0) {
                triggerTier = n;
                break;
            }
        }
        if (triggerTier == 0) {
            return null; // 未达止盈阈值
        }
        // 卖出份额 = holdingShares × (triggerTier / 4)
        BigDecimal holdingShares = capital.holdingShares() != null ? capital.holdingShares() : BigDecimal.ZERO;
        BigDecimal ratio = BigDecimal.valueOf(triggerTier)
                .divide(BigDecimal.valueOf(TRAILING_STOP_TIERS), MATH);
        BigDecimal shares = holdingShares.multiply(ratio, MATH);
        Measure measure = new Measure(shares, MeasureUnit.SHARE);
        return new SignalResult(SignalType.SELL, triggerTier, null, measure, SignalReason.TRAILING_STOP, warnings, List.of());
    }

    /**
     * MIN_HOLD_DAYS。SELL 非逻辑止损未满 5 交易日→降级 NONE+MIN_HOLD_DAYS_NOT_MET;
     * 逻辑止损豁免但记 MIN_HOLD_DAYS_OVERRIDDEN。
     */
    private static SignalResult applyMinHoldDays(SignalResult result, long tradingDaysSinceLastBuy,
                                                 List<SignalWarningValue> warnings) {
        boolean logicBroken = result.reason() == SignalReason.LOGIC_BROKEN;
        if (tradingDaysSinceLastBuy < MIN_HOLD_DAYS) {
            if (logicBroken) {
                warnings.add(SignalWarningValue.of(SignalWarning.MIN_HOLD_DAYS_OVERRIDDEN));
                return result; // 逻辑止损豁免
            }
            return new SignalResult(SignalType.NONE, null, null, null, SignalReason.MIN_HOLD_DAYS_NOT_MET,
                    warnings, List.of());
        }
        return result;
    }
}
