package com.fundpilot.backend.strategy.service.support;

import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;

import java.math.BigDecimal;

/**
 * 回测策略参数(issue #11 重构):从 {@code FundStrategyEntity} + {@code FundEntity} 派生。
 *
 * @param tier1Drawdown           一档回撤阈值
 * @param tier2Drawdown           二档回撤阈值
 * @param tier3Drawdown           三档回撤阈值
 * @param tier4Drawdown           四档回撤阈值
 * @param tier1Ratio              一档加仓比例
 * @param tier2Ratio              二档加仓比例
 * @param tier3Ratio              三档加仓比例
 * @param tier4Ratio              四档加仓比例
 * @param weeklyCoolDownThreshold 周跌幅冷静阈值
 * @param stopLossPullbackPercent 移动止盈回落幅度
 * @param plannedTotalAmount      计划总仓位
 * @param fundCategory            基金类型(单只仓位上限/逻辑止损分派用)
 * @param fundSubType             基金子类型(逻辑止损第三条分派用)
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
        BigDecimal plannedTotalAmount,
        FundCategory fundCategory,
        FundSubType fundSubType) {
}
