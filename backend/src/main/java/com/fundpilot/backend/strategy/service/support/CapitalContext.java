package com.fundpilot.backend.strategy.service.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * 资金与仓位上下文:信号引擎纯函数所需的所有派生值,由调用方(SignalGenerationJob)
 * 先用 {@code FundPositionService} / {@code FundTransactionRepository} 取值再注入。
 * <p>evaluateSignal 零 DB 依赖,便于单测构造数值即可覆盖各分支。
 *
 * <p>行情工作台转向后移除了 totalEquityPct(总权益仓位占比,原总仓位≤80%硬约束的分母,
 * 依赖已删除的 totalInvestableCapital)。totalEquityAmount 保留——单只/单类占比的分母。
 *
 * @param peakNav              前高(基金历史最高累计净值),加仓档位判定基准
 * @param holdingPeriodPeakNav 持有期高点(建仓后最高累计净值),移动止盈判定基准
 * @param singlePositionPct    单只基金当前占比(实际持仓金额 / 总权益持仓金额),硬约束②
 * @param categoryPositionPct  该基金类型当前总占比,硬约束③
 * @param totalEquityAmount    总权益持仓金额(所有基金持仓金额之和),单只/单类占比的分母
 * @param plannedTotalAmount   计划总仓位(BUILD/ADD 建议金额的基数)
 * @param buildShares          建仓交易实际入账份额(移动止盈第四档连卖用)
 * @param tierAddShares        各档加仓交易实际入账份额(key 1~4),移动止盈按档卖出的份额来源(A1 规则)
 * @param holdingShares        当前持仓份额
 * @param lastBuyConfirmTime   最近一次买入确认时间(max(openedAt, tier1-4AddedAt)),MIN_HOLD_DAYS 起算点
 */
public record CapitalContext(
        BigDecimal peakNav,
        BigDecimal holdingPeriodPeakNav,
        BigDecimal singlePositionPct,
        BigDecimal categoryPositionPct,
        BigDecimal totalEquityAmount,
        BigDecimal plannedTotalAmount,
        BigDecimal buildShares,
        Map<Integer, BigDecimal> tierAddShares,
        BigDecimal holdingShares,
        Instant lastBuyConfirmTime) {
}
