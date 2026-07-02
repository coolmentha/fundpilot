package com.fundpilot.backend.strategy.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import com.fundpilot.backend.signal.enums.SignalReason;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import com.fundpilot.backend.strategy.service.support.SignalResult;
import com.fundpilot.backend.strategy.service.support.TakeProfitParams;
import com.fundpilot.backend.strategy.service.support.TrailingStopEngine;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 信号引擎(ADR-0015 重写):定投移动止盈单日判定。买入不经信号(定投定时任务自动扣款),
 * 本引擎只判止盈卖出。核心判定委托纯函数 {@link TrailingStopEngine}——回测模拟器与生产信号引擎
 * 共用同一套策略逻辑(PRD Testing Decisions「测回测等于测策略本体」)。
 *
 * <p>本类为 Spring Service 入口,但 {@link #evaluate} 仍为纯函数:所有外部值由调用方预注入,
 * 便于单测构造数值覆盖分支(#62 SignalGenerationService 负责从 DB 装配后调用本类)。
 *
 * <h3>SELL 信号优先级</h3>
 * 一只基金每日一行 SignalLog,SELL 信号最多一类。evaluate 按顺序检查:
 * 极端行情保护({@code EXTREME_MARKET_PROTECT},#59 实现)> 移动止盈({@code TRAILING_STOP}),命中即返回。
 *
 * <h3>各 NONE 分支 reason</h3>
 * <ul>
 *   <li>{@link SignalReason#FUND_CLEARED}:基金已清仓</li>
 *   <li>{@link SignalReason#NO_STRATEGY}:无生效策略</li>
 *   <li>{@link SignalReason#INSUFFICIENT_MARKET_DATA}:行情数据不足</li>
 *   <li>{@link SignalReason#COOLDOWN_ACTIVE}:卖出冷却期内</li>
 *   <li>{@link SignalReason#FLOOR_REACHED}:底仓保留不卖</li>
 *   <li>{@link SignalReason#NOT_YET_ACTIVATED}:未达启动门槛</li>
 * </ul>
 */
@Service
public class DisciplineStrategyService {

    /**
     * 单日信号判定。纯函数。
     *
     * @param fund          基金(含 status)
     * @param strategy      生效策略版本;可为 null(无策略)
     * @param state         当日前轮内状态(High/peakYield/止盈线/前日跌破/冷却计数)
     * @param position      当日持仓快照(持仓份额/累计买入/累计卖出/累计投入/落袋现金)
     * @param currentNav    当日累计净值;null 视为行情不足
     * @param params        移动止盈参数(按 fundCategory 分派)
     * @return 信号结果(NONE/SELL + reason + sellShares)
     */
    public SignalResult evaluate(FundEntity fund, FundStrategyEntity strategy,
                                 TrailingStopEngine.State state, TrailingStopEngine.Position position,
                                 BigDecimal currentNav, TakeProfitParams params) {
        return evaluate(fund, strategy, state, position, currentNav, params,
                com.fundpilot.backend.strategy.service.support.ExtremeMarketInput.none());
    }

    /**
     * 带极端行情输入的单日判定(#59/#62):优先级极端保护 > 移动止盈。
     * 极端行情保护由调用方预算单日跌幅/3日累计跌幅后注入;null/none 不触发。
     */
    public SignalResult evaluate(FundEntity fund, FundStrategyEntity strategy,
                                 TrailingStopEngine.State state, TrailingStopEngine.Position position,
                                 BigDecimal currentNav, TakeProfitParams params,
                                 com.fundpilot.backend.strategy.service.support.ExtremeMarketInput extreme) {
        if (fund.getStatus() == com.fundpilot.backend.fund.enums.FundStatus.CLEARED) {
            return SignalResult.none(SignalReason.FUND_CLEARED);
        }
        if (strategy == null || strategy.getStatus() != StrategyParamStatus.EFFECTIVE) {
            return SignalResult.none(SignalReason.NO_STRATEGY);
        }
        if (currentNav == null || currentNav.signum() <= 0) {
            return SignalResult.none(SignalReason.INSUFFICIENT_MARKET_DATA);
        }
        TrailingStopEngine.Step step = TrailingStopEngine.evaluate(state, position, currentNav, params, extreme);
        if (step.decision() == TrailingStopEngine.Decision.SELL) {
            return new SignalResult(com.fundpilot.backend.signal.enums.SignalType.SELL,
                    step.sellShares(), step.reason(), List.of());
        }
        return SignalResult.none(step.reason());
    }
}