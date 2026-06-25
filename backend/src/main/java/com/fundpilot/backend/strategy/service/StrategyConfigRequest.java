package com.fundpilot.backend.strategy.service;

import java.math.BigDecimal;

/**
 * 策略参数配置请求(issue #10):四档回撤阈值 + 四档加仓比例 + 周跌幅冷静阈值 + 移动止盈回落幅度。
 * <p>对应 {@code FundStrategyEntity} 的 tier1-4Drawdown / tier1-4Ratio /
 * weeklyCoolDownThreshold / stopLossPullbackPercent 字段。
 *
 * @param tier1Drawdown            一档回撤阈值
 * @param tier2Drawdown            二档回撤阈值
 * @param tier3Drawdown            三档回撤阈值
 * @param tier4Drawdown            四档回撤阈值
 * @param tier1Ratio               一档加仓比例
 * @param tier2Ratio               二档加仓比例
 * @param tier3Ratio               三档加仓比例
 * @param tier4Ratio               四档加仓比例
 * @param weeklyCoolDownThreshold  周跌幅冷静阈值
 * @param stopLossPullbackPercent  移动止盈回落幅度
 */
public record StrategyConfigRequest(
        BigDecimal tier1Drawdown,
        BigDecimal tier2Drawdown,
        BigDecimal tier3Drawdown,
        BigDecimal tier4Drawdown,
        BigDecimal tier1Ratio,
        BigDecimal tier2Ratio,
        BigDecimal tier3Ratio,
        BigDecimal tier4Ratio,
        BigDecimal weeklyCoolDownThreshold,
        BigDecimal stopLossPullbackPercent) {
}
