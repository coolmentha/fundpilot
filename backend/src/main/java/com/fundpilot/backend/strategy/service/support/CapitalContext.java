package com.fundpilot.backend.strategy.service.support;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 资金与仓位上下文:信号引擎纯函数所需的所有派生值,由调用方(SignalGenerationJob)
 * 先用 {@code FundPositionService} / {@code FundTransactionRepository} 取值再注入。
 * <p>evaluateSignal 零 DB 依赖,便于单测构造数值即可覆盖各分支。
 *
 * <p>行情工作台转向 + 金字塔加仓退场后,本上下文瘦身为卖出纪律专用:
 * 移除 plannedTotalAmount/buildShares/tierAddShares(BUILD/ADD 专属)与
 * singlePositionPct/categoryPositionPct/totalEquityAmount(硬约束专属,随 BUILD/ADD 删除)。
 * 仅保留移动止盈与逻辑止损所需的 4 个字段。
 *
 * @param peakNav              前高(基金历史最高累计净值),逻辑止损回撤基准
 * @param holdingPeriodPeakNav 持有期高点(建仓后最高累计净值),保留为行情派生上下文
 * @param holdingShares        当前持仓份额
 * @param lastBuyConfirmTime   最近一次买入确认时间,逻辑止损豁免告警起算点
 * @param currentUnitNav       与止盈累计净值同一期的单位净值,用于真实份额换算
 * @param currentAccumulatedNav 与止盈单位净值同一期的累计净值,用于周期回撤
 * @param floatingProfit       当前整仓浮盈
 * @param matureRedeemableShares 已满足持有期的可赎回份额
 * @param takeProfitEvaluationEnabled 当前交易日是否允许判断定投止盈回撤
 */
public record CapitalContext(
        BigDecimal peakNav,
        BigDecimal holdingPeriodPeakNav,
        BigDecimal holdingShares,
        Instant lastBuyConfirmTime,
        BigDecimal currentUnitNav,
        BigDecimal currentAccumulatedNav,
        BigDecimal floatingProfit,
        BigDecimal matureRedeemableShares,
        boolean takeProfitEvaluationEnabled) {

    public CapitalContext(BigDecimal peakNav, BigDecimal holdingPeriodPeakNav,
                          BigDecimal holdingShares, Instant lastBuyConfirmTime) {
        this(peakNav, holdingPeriodPeakNav, holdingShares, lastBuyConfirmTime,
                null, null, BigDecimal.ZERO, BigDecimal.ZERO, false);
    }
}
