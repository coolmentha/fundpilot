package com.fundpilot.backend.strategy.service.support;

import java.math.BigDecimal;

/**
 * 寻优 train 集排序后的候选(issue #28 top-k 择优):{@link OptimizeParamRanker#rankTopK} 返回,
 * 携带 train 集风险调整收益供编排层择优与诊断展示。
 *
 * @param params      候选参数组合
 * @param trainCalmar 该参数在 train 集的风险调整收益(收益/最大回撤);回撤为 0 时 null(表 +∞)
 * @param trainReturn 该参数在 train 集的策略收益
 * @param trainMaxDrawdown 该参数在 train 集的最大回撤
 */
public record RankedParam(
        OptimizeParams params,
        BigDecimal trainCalmar,
        BigDecimal trainReturn,
        BigDecimal trainMaxDrawdown) {
}
