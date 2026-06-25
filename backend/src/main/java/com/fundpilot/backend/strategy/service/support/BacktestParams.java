package com.fundpilot.backend.strategy.service.support;

import java.math.BigDecimal;

/**
 * 回测策略参数(issue #11):从 {@code FundStrategyEntity} + {@code plannedTotalAmount} 派生。
 *
 * @param tier1Drawdown           一档回撤阈值
 * @param tier2Drawdown           二档回撤阈值
 * @param tier3Drawdown           三档回撤阈值
 * @param tier4Drawdown           四档回撤阈值
 * @param tier1Ratio              一档加仓比例
 * @param tier2Ratio              二档加仓比例
 * @param tier3Ratio              三档加仓比例
 * @param tier4Ratio              四档加仓比例
 * @param weeklyCoolDownThreshold 周跌幅冷静阈值(回测简化版暂不用,留 #12 对齐)
 * @param stopLossPullbackPercent 移动止盈回落幅度
 * @param plannedTotalAmount      计划总仓位
 */
public record BacktestParams(
        BigDecimal tier1Drawdown,
        BigDecimal tier2Drawdown,
        BigDecimal tier3Drawdown,
        BigDecimal tier4Drawdown,
        BigDecimal tier1Ratio,
        BigDecimal tier2Ratio,
        BigDecimal tier3Ratio,
        BigDecimal tier4Ratio,
        BigDecimal weeklyCoolDownThreshold,
        BigDecimal stopLossPullbackPercent,
        BigDecimal plannedTotalAmount) {
}
