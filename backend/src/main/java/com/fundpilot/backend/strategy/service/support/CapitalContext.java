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
 * @param holdingPeriodPeakNav 持有期高点(建仓后最高累计净值),移动止盈回落判定基准
 * @param holdingShares        当前持仓份额(移动止盈按回落分档减仓的份额基数)
 * @param lastBuyConfirmTime   最近一次买入确认时间,MIN_HOLD_DAYS 起算点
 */
public record CapitalContext(
        BigDecimal peakNav,
        BigDecimal holdingPeriodPeakNav,
        BigDecimal holdingShares,
        Instant lastBuyConfirmTime) {
}
