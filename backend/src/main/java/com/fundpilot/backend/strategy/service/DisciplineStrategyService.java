package com.fundpilot.backend.strategy.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import com.fundpilot.backend.fund.service.support.HardConstraintConfig;
import com.fundpilot.backend.signal.enums.MeasureUnit;
import com.fundpilot.backend.signal.enums.SignalType;
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
 * 信号引擎(issue #12):纯函数 {@code evaluateSignal} 按 CONTEXT.md 九步流程对单只基金产出策略建议。
 * <p>零 Spring/DB 依赖——所有外部值(峰值、占比、份额、行情指标)由调用方预注入,
 * 便于单测构造数值即可覆盖各分支。#13 SignalGenerationJob 负责取数后调用本类。
 *
 * <h3>九步流程</h3>
 * <ol>
 *   <li>状态门控:CLEARED→NONE;PENDING_HOLDING→只能 BUILD;HOLDING→可 ADD/SELL</li>
 *   <li>策略生效:status≠EFFECTIVE→NONE(NO_STRATEGY)</li>
 *   <li>回撤派生:(currentNav - peakNav) / peakNav,负数表示跌幅,HOLDING 才算</li>
 *   <li>反弹清空:drawdown &gt; tier1Drawdown - 0.5%(回升变浅)时清空已加标记(warnings 记 TIER_CLEARED)</li>
 *   <li>决策动作:SELL 优先级 逻辑止损&gt;移动止盈&gt;再平衡;否则 ADD 档位;否则 BUILD 三条件</li>
 *   <li>加 warnings:WEEKLY_COOLDOWN / BREAKDOWN_WATCH / INSUFFICIENT_DATA_FOR_COOLDOWN</li>
 *   <li>硬约束:BUILD/ADD 调 check5,违规则 NONE(HARD_CONSTRAINT_BREACH)</li>
 *   <li>MIN_HOLD_DAYS:SELL(非逻辑止损)未满 5 交易日→NONE;逻辑止损豁免但记 OVERRIDDEN</li>
 *   <li>组装 SignalResult</li>
 * </ol>
 *
 * <p>回撤约定:{@code DefaultTierTable} 阈值为负数(如 -0.08 表跌 8%),drawdown 同为负数。
 * "更跌"= drawdown 更小(更负),用 {@code compareTo <= 0} 判断跌破;"回升变浅"= drawdown 更大(接近 0)。
 */
@Service
public class DisciplineStrategyService {

    private static final MathContext MATH = MathContext.DECIMAL64;

    /**
     * @param fund                      基金(含 status/fundCategory/fundSubType 等)
     * @param strategy                  生效策略版本;可为 null(无策略)
     * @param market                    行情指标快照
     * @param capital                   资金与仓位上下文
     * @param today                     信号生成日(14:50)
     * @param tradingDaysSinceLastBuy   最近一次买入确认至今的交易日数(MIN_HOLD_DAYS 判定,由调用方用 TradingCalendarService 预算)
     * @return 信号结果(NONE/BUILD/ADD/SELL + reason/warnings/breaches)
     */
    public SignalResult evaluateSignal(FundEntity fund, FundStrategyEntity strategy,
                                       MarketIndicators market, CapitalContext capital, Instant today,
                                       long tradingDaysSinceLastBuy) {
        // 步骤 1:状态门控
        FundStatus status = fund.getStatus();
        if (status == FundStatus.CLEARED) {
            return SignalResult.none("FUND_CLEARED");
        }
        // 步骤 2:策略生效
        if (strategy == null || strategy.getStatus() != StrategyParamStatus.EFFECTIVE) {
            return SignalResult.none("NO_STRATEGY");
        }

        List<String> warnings = new ArrayList<>();

        // 步骤 3:回撤派生(HOLDING 才算;PENDING_HOLDING 无前高)
        BigDecimal drawdown = deriveDrawdown(status, market, capital);

        // 步骤 4:反弹清空(HOLDING 才清;PENDING_HOLDING 无档位标记)
        if (status == FundStatus.HOLDING) {
            clearTiersOnRebound(strategy, drawdown, warnings);
        }

        // 步骤 5:决策动作
        SignalResult result = decideAction(fund, strategy, market, capital, status, drawdown, warnings);

        // 步骤 6:加仓信号专属 warnings(BUILD/ADD)
        if (result.signalType() == SignalType.BUILD || result.signalType() == SignalType.ADD) {
            addWarnings(strategy, market, capital, warnings);
        }

        // 步骤 7:硬约束(BUILD/ADD 才检查;违规则降级 NONE)
        if (result.signalType() == SignalType.BUILD || result.signalType() == SignalType.ADD) {
            result = applyHardConstraints(fund, strategy, capital, result, warnings);
        }

        // 步骤 8:MIN_HOLD_DAYS(SELL 非逻辑止损未满 5 交易日→降级 NONE;逻辑止损豁免但记 OVERRIDDEN)
        if (result.signalType() == SignalType.SELL) {
            result = applyMinHoldDays(result, tradingDaysSinceLastBuy, warnings);
        }

        // 步骤 9:组装(重建 SignalResult 以携带完整 warnings)
        return new SignalResult(result.signalType(), result.triggerTier(), result.coefficient(),
                result.suggestedMeasure(), result.reason(), List.copyOf(warnings), result.hardConstraintBreaches());
    }

    /** 步骤 3:drawdown = (currentNav - peakNav) / peakNav,负数表示跌幅。peakNav 缺失或为 0 时返 0(不跌)。 */
    private BigDecimal deriveDrawdown(FundStatus status, MarketIndicators market, CapitalContext capital) {
        if (status != FundStatus.HOLDING) {
            return BigDecimal.ZERO;
        }
        BigDecimal peakNav = capital.peakNav();
        BigDecimal currentNav = market.currentNav();
        if (peakNav == null || peakNav.signum() <= 0 || currentNav == null) {
            return BigDecimal.ZERO;
        }
        return currentNav.subtract(peakNav).divide(peakNav, MATH);
    }

    /**
     * 步骤 4:反弹清空。drawdown 回升到比 tier1Drawdown 浅 0.5%(缓冲带)时,清空所有已加标记。
     * 逐档检查更深档:若 drawdown > tierNDrawdown - 0.005 则该档被"真正"脱离,清空它及更深的档。
     * 只动字段(tierNAddedAt 置 null)不动交易;warnings 记 TIER_CLEARED。
     */
    private void clearTiersOnRebound(FundStrategyEntity strategy, BigDecimal drawdown, List<String> warnings) {
        BigDecimal buffer = HardConstraintConfig.TIER_CLEAR_BUFFER;
        List<Integer> cleared = new ArrayList<>();
        // 从深档往浅档检查:drawdown > tierNDrawdown - buffer 表示已脱离该档(回升变浅)
        for (int tier = 4; tier >= 1; tier--) {
            Instant addedAt = tierAddedAt(strategy, tier);
            if (addedAt == null) {
                continue;
            }
            BigDecimal tierDrawdown = tierDrawdown(strategy, tier);
            // drawdown(负) > tierDrawdown - buffer(更接近0) 表示净值回升,已脱离该档
            if (drawdown.compareTo(tierDrawdown.subtract(buffer)) > 0) {
                setTierAddedAt(strategy, tier, null);
                cleared.add(tier);
            }
        }
        if (!cleared.isEmpty()) {
            warnings.add("TIER_CLEARED:" + cleared.stream().sorted().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(""));
        }
    }

    /** 步骤 5:决策动作。SELL 优先级 > ADD > BUILD;PENDING_HOLDING 只能 BUILD。 */
    private SignalResult decideAction(FundEntity fund, FundStrategyEntity strategy,
                                      MarketIndicators market, CapitalContext capital,
                                      FundStatus status, BigDecimal drawdown, List<String> warnings) {
        if (status == FundStatus.PENDING_HOLDING) {
            return decideBuild(fund, strategy, market, capital, warnings);
        }
        // HOLDING:循环 C 补 SELL(逻辑止损/移动止盈/再平衡);此处先做 ADD
        SignalResult sellResult = decideSell(fund, strategy, market, capital, drawdown, warnings);
        if (sellResult != null) {
            return sellResult;
        }
        return decideAdd(fund, strategy, market, capital, drawdown, warnings);
    }

    /** BUILD:三条件全满足(年线上方 + 年线向上 + 60 日新高)。建议金额 = plannedTotalAmount × 0.10。 */
    private SignalResult decideBuild(FundEntity fund, FundStrategyEntity strategy,
                                     MarketIndicators market, CapitalContext capital, List<String> warnings) {
        if (!market.priceAboveYearLine() || !market.yearLineRising() || !market.sixtyDayHigh()) {
            return new SignalResult(SignalType.NONE, null, null, null, "BUILD_CONDITION_NOT_MET", warnings, List.of());
        }
        BigDecimal amount = capital.plannedTotalAmount().multiply(HardConstraintConfig.BUILD_RATIO, MATH);
        Measure measure = new Measure(amount, MeasureUnit.AMOUNT);
        // 循环 D 补 warnings/硬约束
        return new SignalResult(SignalType.BUILD, null, BigDecimal.ONE, measure, "BUILD", warnings, List.of());
    }

    /**
     * ADD:遍历 tier 1-4,drawdown <= tierNDrawdown(跌破阈值)且 tierNAddedAt==null(未触发过)→ 触发该档。
     * 建议金额 = plannedTotalAmount × tierRatio × 调节系数(循环 D 补系数)。
     */
    private SignalResult decideAdd(FundEntity fund, FundStrategyEntity strategy,
                                   MarketIndicators market, CapitalContext capital,
                                   BigDecimal drawdown, List<String> warnings) {
        for (int tier = 1; tier <= 4; tier++) {
            if (tierAddedAt(strategy, tier) != null) {
                continue; // 该档已触发
            }
            BigDecimal tierDrawdown = tierDrawdown(strategy, tier);
            if (drawdown.compareTo(tierDrawdown) <= 0) {
                // 跌破该档阈值 → 触发加仓
                BigDecimal ratio = tierRatio(strategy, tier);
                BigDecimal baseAmount = capital.plannedTotalAmount().multiply(ratio, MATH);
                // 调节系数 = 年线 × MACD × 成交量,clamp(0.3, 1.5)
                BigDecimal coefficient = computeCoefficient(market);
                BigDecimal amount = baseAmount.multiply(coefficient, MATH);
                Measure measure = new Measure(amount, MeasureUnit.AMOUNT);
                return new SignalResult(SignalType.ADD, tier, coefficient, measure, "ADD", warnings, List.of());
            }
        }
        return new SignalResult(SignalType.NONE, null, null, null, "NO_ADD_TIER", warnings, List.of());
    }

    /**
     * 步骤 5-SELL:逻辑止损 > 移动止盈 > 再平衡。命中即返回,否则 null(继续 ADD 判定)。
     * SELL 信号最多一类(CONTEXT.md「SELL 信号优先级」)。
     */
    private SignalResult decideSell(FundEntity fund, FundStrategyEntity strategy,
                                    MarketIndicators market, CapitalContext capital,
                                    BigDecimal drawdown, List<String> warnings) {
        // 1. 逻辑止损(优先级最高,豁免 MIN_HOLD_DAYS)
        SignalResult logicBroken = checkLogicBrokenStopLoss(fund, strategy, market, capital, warnings);
        if (logicBroken != null) {
            return logicBroken;
        }
        // 2. 移动止盈
        SignalResult trailingStop = checkTrailingStop(fund, strategy, market, capital, warnings);
        if (trailingStop != null) {
            return trailingStop;
        }
        // 3. 再平衡减仓
        SignalResult rebalance = checkRebalance(fund, market, capital, warnings);
        if (rebalance != null) {
            return rebalance;
        }
        return null;
    }

    /**
     * 逻辑止损:趋势死亡型,一次清空。按 fundSubType 分派:
     * <ul>
     *   <li>ETF/INDEX/INDEX_ENHANCED:破年线 + MACD绿柱扩大 + 跟踪指数放量下跌(当日量>20日均量×1.5 且 当日收跌)</li>
     *   <li>ACTIVE:破年线 + MACD绿柱扩大 + 单周跌幅 > weeklyCoolDownThreshold</li>
     * </ul>
     * 触发后 tier1~4AddedAt 全清,reason=LOGIC_BROKEN。MIN_HOLD_DAYS 豁免(循环 D 记 OVERRIDDEN)。
     */
    private SignalResult checkLogicBrokenStopLoss(FundEntity fund, FundStrategyEntity strategy,
                                                  MarketIndicators market,
                                                  CapitalContext capital, List<String> warnings) {
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
            // 主动基金:单周跌幅 > weeklyCoolDownThreshold
            BigDecimal drop = market.weeklyDropPercent();
            BigDecimal threshold = strategy.getWeeklyCoolDownThreshold();
            condition3 = drop != null && threshold != null && drop.compareTo(threshold) > 0;
        } else {
            // ETF/INDEX/INDEX_ENHANCED:跟踪指数放量下跌
            condition3 = market.benchmarkVolumeState() == com.fundpilot.backend.market.enums.VolumeState.HIGH_DROP
                    && market.benchmarkDroppedToday();
        }
        if (!condition3) {
            return null;
        }
        // 触发:一次清空(全卖 holdingShares),tier1~4AddedAt 全清
        BigDecimal shares = capital.holdingShares() != null ? capital.holdingShares() : BigDecimal.ZERO;
        Measure measure = new Measure(shares, MeasureUnit.SHARE);
        // 注:tier 字段清空由调用方(#13 Job 在 confirmOperation 时处理),此处仅出信号
        return new SignalResult(SignalType.SELL, null, null, measure, "LOGIC_BROKEN", warnings, List.of());
    }

    /**
     * 移动止盈:从 holdingPeriodPeakNav 回落 n×stopLossPullbackPercent 触发卖第 n 档(倒序:深档先卖)。
     * 空档轮空:从应触发档往浅档找第一个 tierNAddedAt!=null 的档;都找不到→返 null(NO_TIER_TO_SELL)。
     * 第四档触发时连卖 buildShares(建仓份额)。份额来自 tierAddShares map(A1 规则)。
     */
    private SignalResult checkTrailingStop(FundEntity fund, FundStrategyEntity strategy,
                                           MarketIndicators market, CapitalContext capital,
                                           List<String> warnings) {
        BigDecimal peak = capital.holdingPeriodPeakNav();
        BigDecimal currentNav = market.currentNav();
        if (peak == null || peak.signum() <= 0 || currentNav == null) {
            return null;
        }
        // 回落幅度 = (peak - current) / peak,正数
        BigDecimal pullback = peak.subtract(currentNav).divide(peak, MATH);
        BigDecimal stopLossPercent = strategy.getStopLossPullbackPercent();
        if (stopLossPercent == null || stopLossPercent.signum() <= 0) {
            return null;
        }
        // 计算应触发的档位:pullback >= n × stopLossPercent,n 从 4 降到 1,取最大 n
        int triggerTier = 0;
        for (int n = 4; n >= 1; n--) {
            if (pullback.compareTo(stopLossPercent.multiply(BigDecimal.valueOf(n), MATH)) >= 0) {
                triggerTier = n;
                break;
            }
        }
        if (triggerTier == 0) {
            return null; // 未达任何档止盈阈值
        }
        // 空档轮空:从 triggerTier 往浅档找第一个 tierNAddedAt!=null 的档
        int sellTier = 0;
        for (int n = triggerTier; n >= 1; n--) {
            if (tierAddedAt(strategy, n) != null) {
                sellTier = n;
                break;
            }
        }
        if (sellTier == 0) {
            // 无可卖档位,不产生 SELL(warnings 可提示,但 spec 说 NO_TIER_TO_SELL 是 NONE)
            return new SignalResult(SignalType.NONE, null, null, null, "NO_TIER_TO_SELL", warnings, List.of());
        }
        // 份额:第 sellTier 档加仓份额;第四档连卖 buildShares
        BigDecimal shares = capital.tierAddShares() != null
                ? capital.tierAddShares().getOrDefault(sellTier, BigDecimal.ZERO)
                : BigDecimal.ZERO;
        if (sellTier == 4 && capital.buildShares() != null) {
            shares = shares.add(capital.buildShares());
        }
        Measure measure = new Measure(shares, MeasureUnit.SHARE);
        return new SignalResult(SignalType.SELL, sellTier, null, measure, "TRAILING_STOP", warnings, List.of());
    }

    /**
     * 再平衡减仓:单只基金占比 > singlePositionLimit(宽基/主动/混合 20%,行业 15%)。
     * 卖出金额 = (当前占比 - 上限) × 总权益持仓金额,按当前净值反算为份额。
     * 遵守 MIN_HOLD_DAYS(循环 D 处理降级);不清档位。
     */
    private SignalResult checkRebalance(FundEntity fund, MarketIndicators market,
                                        CapitalContext capital, List<String> warnings) {
        BigDecimal singlePct = capital.singlePositionPct();
        if (singlePct == null) {
            return null;
        }
        BigDecimal limit = HardConstraintConfig.singlePositionLimit(fund.getFundCategory());
        if (singlePct.compareTo(limit) <= 0) {
            return null; // 未超限
        }
        BigDecimal totalEquityAmount = capital.totalEquityAmount();
        BigDecimal currentNav = market.currentNav();
        if (totalEquityAmount == null || totalEquityAmount.signum() <= 0 || currentNav == null || currentNav.signum() <= 0) {
            return null;
        }
        // 卖出金额 = (占比 - 上限) × 总权益持仓金额;份额 = 金额 / 当前净值
        BigDecimal sellAmount = singlePct.subtract(limit).multiply(totalEquityAmount, MATH);
        BigDecimal shares = sellAmount.divide(currentNav, MATH);
        Measure measure = new Measure(shares, MeasureUnit.SHARE);
        return new SignalResult(SignalType.SELL, null, null, measure, "REBALANCE", warnings, List.of());
    }

    // ---- 步骤 6-8: warnings / 硬约束 / MIN_HOLD_DAYS ----

    /** 调节系数:年线 × MACD × 成交量,clamp(0.3, 1.5)。 */
    private static BigDecimal computeCoefficient(MarketIndicators market) {
        com.fundpilot.backend.fund.service.support.YearLineState yearLineState = toYearLineState(market);
        BigDecimal y = com.fundpilot.backend.fund.service.support.CoefficientTable.yearLine(yearLineState);
        BigDecimal m = com.fundpilot.backend.fund.service.support.CoefficientTable.macd(market.weeklyMacdState());
        BigDecimal v = com.fundpilot.backend.fund.service.support.CoefficientTable.volume(market.volumeState());
        return com.fundpilot.backend.fund.service.support.CoefficientCombiner.combine(y, m, v);
    }

    /** 年线状态:上方且向上=ABOVE_RISING,上方但向下=ABOVE_FALLING,下方且向下=BELOW_FALLING。 */
    private static com.fundpilot.backend.fund.service.support.YearLineState toYearLineState(MarketIndicators market) {
        if (market.priceAboveYearLine() && market.yearLineRising()) {
            return com.fundpilot.backend.fund.service.support.YearLineState.ABOVE_RISING;
        }
        if (market.priceAboveYearLine() && !market.yearLineRising()) {
            return com.fundpilot.backend.fund.service.support.YearLineState.ABOVE_FALLING;
        }
        return com.fundpilot.backend.fund.service.support.YearLineState.BELOW_FALLING;
    }

    /**
     * 步骤 6:加仓信号 warnings。
     * <ul>
     *   <li>WEEKLY_COOLDOWN:单周跌幅 > weeklyCoolDownThreshold(数据不足时 INSUFFICIENT_DATA_FOR_COOLDOWN)</li>
     *   <li>BREAKDOWN_WATCH:年线下方且向下(系数0.4) 且 放量下跌(系数0.5)</li>
     * </ul>
     */
    private static void addWarnings(FundStrategyEntity strategy, MarketIndicators market,
                                    CapitalContext capital, List<String> warnings) {
        BigDecimal drop = market.weeklyDropPercent();
        BigDecimal threshold = strategy.getWeeklyCoolDownThreshold();
        if (drop == null) {
            warnings.add("INSUFFICIENT_DATA_FOR_COOLDOWN");
        } else if (threshold != null && drop.compareTo(threshold) > 0) {
            warnings.add("WEEKLY_COOLDOWN");
        }
        if (!market.priceAboveYearLine() && !market.yearLineRising()
                && market.volumeState() == com.fundpilot.backend.market.enums.VolumeState.HIGH_DROP) {
            warnings.add("BREAKDOWN_WATCH");
        }
    }

    /**
     * 步骤 7:硬约束。BUILD/ADD 调 {@code HardConstraintChecker.check5},违规则降级 NONE+HARD_CONSTRAINT_BREACH。
     * singleAddRatio = 本次加仓金额 / plannedTotalAmount。
     */
    private static SignalResult applyHardConstraints(FundEntity fund, FundStrategyEntity strategy,
                                                     CapitalContext capital, SignalResult result,
                                                     List<String> warnings) {
        // buildRatio 只在建仓信号时为 BUILD_RATIO,加仓信号时为 0(已建仓不再判建仓比例)
        BigDecimal buildRatio = result.signalType() == SignalType.BUILD
                ? HardConstraintConfig.BUILD_RATIO : BigDecimal.ZERO;
        BigDecimal addRatio = result.signalType() == SignalType.ADD
                ? tierRatio(strategy, result.triggerTier()) : BigDecimal.ZERO;
        BigDecimal singleAddRatio = addRatio; // 本次加仓比例(建仓时 singleAddRatio=0)
        java.util.List<com.fundpilot.backend.fund.service.support.Breach> breaches =
                com.fundpilot.backend.fund.service.support.HardConstraintChecker.check5(
                        fund.getFundCategory(), buildRatio,
                        capital.singlePositionPct(), capital.categoryPositionPct(),
                        capital.totalEquityPct(), singleAddRatio);
        if (breaches.isEmpty()) {
            return result;
        }
        return new SignalResult(SignalType.NONE, null, null, null, "HARD_CONSTRAINT_BREACH",
                warnings, breaches);
    }

    /**
     * 步骤 8:MIN_HOLD_DAYS。SELL 非逻辑止损未满 5 交易日→降级 NONE+MIN_HOLD_DAYS_NOT_MET;
     * 逻辑止损豁免但记 MIN_HOLD_DAYS_OVERRIDDEN。
     */
    private static SignalResult applyMinHoldDays(SignalResult result, long tradingDaysSinceLastBuy,
                                                 List<String> warnings) {
        boolean logicBroken = "LOGIC_BROKEN".equals(result.reason());
        if (tradingDaysSinceLastBuy < HardConstraintConfig.MIN_HOLD_DAYS) {
            if (logicBroken) {
                warnings.add("MIN_HOLD_DAYS_OVERRIDDEN");
                return result; // 逻辑止损豁免
            }
            return new SignalResult(SignalType.NONE, null, null, null, "MIN_HOLD_DAYS_NOT_MET",
                    warnings, List.of());
        }
        return result;
    }

    // ---- tier 字段访问辅助 ----

    private static Instant tierAddedAt(FundStrategyEntity strategy, int tier) {
        return switch (tier) {
            case 1 -> strategy.getTier1AddedAt();
            case 2 -> strategy.getTier2AddedAt();
            case 3 -> strategy.getTier3AddedAt();
            case 4 -> strategy.getTier4AddedAt();
            default -> throw new IllegalArgumentException("tier 必须是 1~4: " + tier);
        };
    }

    private static void setTierAddedAt(FundStrategyEntity strategy, int tier, Instant value) {
        switch (tier) {
            case 1 -> strategy.setTier1AddedAt(value);
            case 2 -> strategy.setTier2AddedAt(value);
            case 3 -> strategy.setTier3AddedAt(value);
            case 4 -> strategy.setTier4AddedAt(value);
            default -> throw new IllegalArgumentException("tier 必须是 1~4: " + tier);
        }
    }

    private static BigDecimal tierDrawdown(FundStrategyEntity strategy, int tier) {
        return switch (tier) {
            case 1 -> strategy.getTier1Drawdown();
            case 2 -> strategy.getTier2Drawdown();
            case 3 -> strategy.getTier3Drawdown();
            case 4 -> strategy.getTier4Drawdown();
            default -> throw new IllegalArgumentException("tier 必须是 1~4: " + tier);
        };
    }

    private static BigDecimal tierRatio(FundStrategyEntity strategy, int tier) {
        return switch (tier) {
            case 1 -> strategy.getTier1Ratio();
            case 2 -> strategy.getTier2Ratio();
            case 3 -> strategy.getTier3Ratio();
            case 4 -> strategy.getTier4Ratio();
            default -> throw new IllegalArgumentException("tier 必须是 1~4: " + tier);
        };
    }
}
