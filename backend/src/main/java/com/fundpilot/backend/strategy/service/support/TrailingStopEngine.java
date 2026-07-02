package com.fundpilot.backend.strategy.service.support;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/**
 * 移动止盈策略核心引擎(issue #57,ADR-0015):纯函数,逐日判定是否触发止盈卖出。
 * <p>回测模拟器与生产信号引擎共用本类——「测回测等于测策略本体」(PRD Testing Decisions)。
 *
 * <h3>状态模型</h3>
 * <ul>
 *   <li>{@link State}:轮内状态(High、peakYield、是否启动、止盈线、前日是否跌破、上次卖出至今交易日数),
 *       卖出后重置(High 从剩余仓位重算、重新等启动门槛)——High 只增不减指<strong>轮内</strong>。</li>
 *   <li>{@link Position}:持仓快照(持仓份额、累计买入份额、累计卖出份额、累计投入、落袋现金),由调用方维护。</li>
 * </ul>
 *
 * <h3>判定流程(单日)</h3>
 * <ol>
 *   <li>算今日市值 mv、收益率 yield = (mv − invested) / invested。</li>
 *   <li>轮内 High = max(旧 High, mv);轮内 peakYield = max(旧 peakYield, yield)。</li>
 *   <li>启动:peakYield ≥ 启动门槛才视为已启动;未启动不判止盈。</li>
 *   <li>启动后:回撤比例按 peakYield 分级查表,目标止盈线 = High × (1 − 回撤比例);
 *       <strong>止盈线只上移不降</strong>:实际止盈线 = max(旧止盈线, 目标线)。</li>
 *   <li>今日跌破止盈线 且 前日也跌破(连续 2 日) 且 冷却期(tradingDaysSinceLastSell ≥ cooldownDays)已过
 *       且 未达底仓上限(累计卖出比例 < 1 − floorRatio)→ 卖当前持仓 × sellRatio。</li>
 *   <li>卖出后轮内状态重置:High = 卖后剩余市值、peakYield 重算、stopLine 重置、重新等启动门槛、冷却计数归零。</li>
 * </ol>
 *
 * <p>本类无 Spring/DB 依赖,所有值由参数注入,便于单测构造数值覆盖各分支。
 */
public final class TrailingStopEngine {

    private static final MathContext MATH = MathContext.DECIMAL64;
    /** 未卖出时的冷却计数初值:足够大,确保首次判定不被冷却期拦截。 */
    static final int NO_COOLDOWN = 10_000;
    /** 极端行情保护阈值(CONTEXT.md「极端行情保护」):单日跌 ≥7%。 */
    private static final BigDecimal EXTREME_SINGLE_DAY_DROP = new BigDecimal("0.07");
    /** 极端行情保护阈值:连续3日累计跌 ≥12%。 */
    private static final BigDecimal EXTREME_3DAY_DROP = new BigDecimal("0.12");
    /** 极端行情保护卖出比例:10~20% 区间中值 15%。 */
    private static final BigDecimal EXTREME_SELL_RATIO = new BigDecimal("0.15");

    private TrailingStopEngine() {
    }

    /** 轮内状态(卖出后重置)。 */
    public record State(
            BigDecimal roundHigh,
            BigDecimal roundPeakYield,
            boolean activated,
            BigDecimal roundStopLine,
            boolean prevDayBelowLine,
            int tradingDaysSinceLastSell) {

        /** 轮内初始状态:High/peakYield/stopLine 为 0,未启动,前日未跌破,冷却计数足够大。 */
        public static State initial() {
            return new State(BigDecimal.ZERO, BigDecimal.ZERO, false, BigDecimal.ZERO, false, NO_COOLDOWN);
        }
    }

    /** 持仓快照(调用方维护,DCA 买入与卖出时更新)。 */
    public record Position(
            BigDecimal holdingShares,
            BigDecimal totalAcquiredShares,
            BigDecimal totalSoldShares,
            BigDecimal invested,
            BigDecimal cashedOut) {
    }

    /** 单日判定输出。{@code reason} 区分 SELL 来源(EXTREME/TRAILING)与 NONE 子原因。 */
    public record Step(Decision decision, BigDecimal sellShares, com.fundpilot.backend.signal.enums.SignalReason reason, State newState) {
    }

    /** 单日决策:NONE(不卖)/SELL(卖出 sellShares 份)。 */
    public enum Decision { NONE, SELL }

    /**
     * 逐日判定。纯函数:不修改入参,返回新状态与决策。
     *
     * @param state      当日<strong>前</strong>的轮内状态
     * @param position   当日持仓快照(DCA 买入已由调用方应用)
     * @param currentNav 当日累计净值
     * @param params     移动止盈参数
     * @return 当日决策 + 卖出份额(SELL 时) + 更新后的轮内状态
     */
    public static Step evaluate(State state, Position position, BigDecimal currentNav, TakeProfitParams params) {
        return evaluate(state, position, currentNav, params, ExtremeMarketInput.none());
    }

    /**
     * 逐日判定(带极端行情输入)。纯函数:不修改入参,返回新状态与决策。
     * <p>优先级:极端行情保护({@code EXTREME_MARKET_PROTECT},#59)> 移动止盈({@code TRAILING_STOP}),命中即返回。
     *
     * @param state      当日<strong>前</strong>的轮内状态
     * @param position   当日持仓快照(DCA 买入已由调用方应用)
     * @param currentNav 当日累计净值
     * @param params     移动止盈参数
     * @param extreme    极端行情输入(单日跌/3日累计跌);null 或空值不触发
     * @return 当日决策 + 卖出份额(SELL 时) + 更新后的轮内状态
     */
    public static Step evaluate(State state, Position position, BigDecimal currentNav, TakeProfitParams params,
                                ExtremeMarketInput extreme) {
        BigDecimal holdingShares = position.holdingShares();
        BigDecimal invested = position.invested();
        BigDecimal mv = holdingShares.multiply(currentNav, MATH);
        BigDecimal yield = invested.signum() > 0
                ? mv.subtract(invested).divide(invested, MATH)
                : BigDecimal.ZERO;
        boolean profitable = yield.signum() > 0;

        // 轮内 High / peakYyield 只增不减
        BigDecimal roundHigh = mv.max(state.roundHigh());
        BigDecimal roundPeakYield = yield.max(state.roundPeakYield());
        boolean activated = state.activated() || roundPeakYield.compareTo(params.activationThreshold()) >= 0;

        // 止盈线只上移不降:目标线 = High×(1−ratio);实际线 = max(旧线, 目标线)
        BigDecimal roundStopLine = state.roundStopLine();
        boolean todayBelowLine = false;
        if (activated) {
            BigDecimal ratio = pickPullbackRatio(params.pullbackTiers(), roundPeakYield);
            BigDecimal targetLine = roundHigh.multiply(BigDecimal.ONE.subtract(ratio, MATH), MATH);
            roundStopLine = roundStopLine.max(targetLine);
            todayBelowLine = mv.compareTo(roundStopLine) < 0;
        }

        int daysSinceLastSell = state.tradingDaysSinceLastSell() + 1;

        // ① 极端行情保护(优先级高于移动止盈):单日跌≥7%且盈利 / 连3日累计跌≥12% → 卖 15%(10~20%区间中值)
        if (extreme != null && profitable && !floorReached(position, params)
                && daysSinceLastSell >= params.cooldownDays()) {
            BigDecimal daily = extreme.dailyDropPct();
            BigDecimal cum3 = extreme.cumulative3DayDropPct();
            boolean singleDayHit = daily != null && daily.compareTo(EXTREME_SINGLE_DAY_DROP) >= 0;
            boolean threeDayHit = cum3 != null && cum3.compareTo(EXTREME_3DAY_DROP) >= 0;
            if (singleDayHit || threeDayHit) {
                BigDecimal sellShares = holdingShares.multiply(EXTREME_SELL_RATIO, MATH);
                State newState = resetState(position, currentNav, params, sellShares);
                return new Step(Decision.SELL, sellShares,
                        com.fundpilot.backend.signal.enums.SignalReason.EXTREME_MARKET_PROTECT, newState);
            }
        }

        // ② 移动止盈:连续2日跌破 + 冷却期过 + 底仓未满
        Decision decision = Decision.NONE;
        BigDecimal sellShares = BigDecimal.ZERO;
        com.fundpilot.backend.signal.enums.SignalReason reason =
                com.fundpilot.backend.signal.enums.SignalReason.NO_TRIGGER;
        if (activated && todayBelowLine && state.prevDayBelowLine()
                && daysSinceLastSell >= params.cooldownDays()
                && !floorReached(position, params)) {
            decision = Decision.SELL;
            sellShares = holdingShares.multiply(params.sellRatio(), MATH);
            reason = com.fundpilot.backend.signal.enums.SignalReason.TRAILING_STOP;
        } else if (!activated) {
            reason = com.fundpilot.backend.signal.enums.SignalReason.NOT_YET_ACTIVATED;
        } else if (daysSinceLastSell < params.cooldownDays()) {
            reason = com.fundpilot.backend.signal.enums.SignalReason.COOLDOWN_ACTIVE;
        } else if (floorReached(position, params)) {
            reason = com.fundpilot.backend.signal.enums.SignalReason.FLOOR_REACHED;
        }

        State newState;
        if (decision == Decision.SELL) {
            newState = resetState(position, currentNav, params, sellShares);
        } else {
            newState = new State(roundHigh, roundPeakYield, activated, roundStopLine, todayBelowLine, daysSinceLastSell);
        }
        return new Step(decision, sellShares, reason, newState);
    }

    /** 卖出后重置:以剩余仓位重算 High、peakYield、stopLine,重新等启动门槛,冷却计数归零。 */
    private static State resetState(Position position, BigDecimal currentNav, TakeProfitParams params, BigDecimal sellShares) {
        BigDecimal remainingShares = position.holdingShares().subtract(sellShares, MATH);
        BigDecimal remainingMv = remainingShares.multiply(currentNav, MATH);
        BigDecimal invested = position.invested();
        BigDecimal resetYield = invested.signum() > 0 && remainingShares.signum() > 0
                ? remainingMv.subtract(invested).divide(invested, MATH)
                : BigDecimal.ZERO;
        boolean resetActivated = resetYield.compareTo(params.activationThreshold()) >= 0;
        BigDecimal resetStopLine = BigDecimal.ZERO;
        if (resetActivated) {
            BigDecimal ratio = pickPullbackRatio(params.pullbackTiers(), resetYield);
            resetStopLine = remainingMv.multiply(BigDecimal.ONE.subtract(ratio, MATH), MATH);
        }
        return new State(remainingMv, resetYield, resetActivated, resetStopLine, false, 0);
    }

    /** 底仓保留:累计卖出份额 / 累计买入份额 ≥ (1 − floorRatio) 时达上限,不再卖。 */
    private static boolean floorReached(Position position, TakeProfitParams params) {
        BigDecimal acquired = position.totalAcquiredShares();
        if (acquired == null || acquired.signum() <= 0) {
            return true; // 无买入无法算比例,保守视为已达(不卖)
        }
        BigDecimal sold = position.totalSoldShares() == null ? BigDecimal.ZERO : position.totalSoldShares();
        BigDecimal soldPct = sold.divide(acquired, MATH);
        BigDecimal maxSellPct = BigDecimal.ONE.subtract(params.floorRatio(), MATH);
        return soldPct.compareTo(maxSellPct) >= 0;
    }

    /** 按 peakYield 在分级表(升序)中择档:取 minYield ≤ peakYield 的最深档;无命中返 0。 */
    static BigDecimal pickPullbackRatio(List<PullbackTier> tiers, BigDecimal peakYield) {
        BigDecimal ratio = BigDecimal.ZERO;
        for (PullbackTier tier : tiers) {
            if (peakYield.compareTo(tier.minYield()) >= 0) {
                ratio = tier.ratio();
            } else {
                break;
            }
        }
        return ratio;
    }
}