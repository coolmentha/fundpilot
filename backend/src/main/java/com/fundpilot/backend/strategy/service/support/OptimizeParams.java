package com.fundpilot.backend.strategy.service.support;

import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;

import java.math.BigDecimal;

/**
 * 寻优网格的一个候选参数组合(issue #26):由 {@link OptimizeGridGenerator} 生成,
 * 供 {@code OptimizeParamRanker}(issue #27)在 train 集上算风险调整收益排序。
 *
 * <p>3 个搜索维度(tier1Drawdown / tier4Drawdown / stopLossPullbackPercent)从默认值出发扰动;
 * 中间档 tier2/tier3 按默认表相对位置内插;4 档加仓比例(15/20/25/30)与 weeklyCoolDownThreshold
 * 用默认值不搜(CONTEXT.md「寻优搜索维度」「寻优搜索基准」)。
 *
 * @param tier1Drawdown           一档回撤阈值(搜索维度,负数)
 * @param tier2Drawdown           二档回撤阈值(内插,负数)
 * @param tier3Drawdown           三档回撤阈值(内插,负数)
 * @param tier4Drawdown           四档回撤阈值(搜索维度,负数)
 * @param stopLossPullbackPercent 移动止盈回落幅度(搜索维度,正数小数)
 * @param tier1Ratio              一档加仓比例(默认 0.15,不搜)
 * @param tier2Ratio              二档加仓比例(默认 0.20,不搜)
 * @param tier3Ratio              三档加仓比例(默认 0.25,不搜)
 * @param tier4Ratio              四档加仓比例(默认 0.30,不搜)
 * @param weeklyCoolDownThreshold 周跌幅冷静阈值(默认,按 fundCategory 查 DefaultCoolDownTable,不搜)
 * @param fundCategory            基金类型(fund 不变量)
 * @param fundSubType             基金子类型(fund 不变量,留空由编排层填)
 */
public record OptimizeParams(
        BigDecimal tier1Drawdown,
        BigDecimal tier2Drawdown,
        BigDecimal tier3Drawdown,
        BigDecimal tier4Drawdown,
        BigDecimal stopLossPullbackPercent,
        BigDecimal tier1Ratio,
        BigDecimal tier2Ratio,
        BigDecimal tier3Ratio,
        BigDecimal tier4Ratio,
        BigDecimal weeklyCoolDownThreshold,
        FundCategory fundCategory,
        FundSubType fundSubType) {
}
