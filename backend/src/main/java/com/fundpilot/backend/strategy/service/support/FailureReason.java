package com.fundpilot.backend.strategy.service.support;

/**
 * 寻优 test 集未达标的具体原因(issue #28 诊断增强)。
 *
 * <p>归因到原子级——真实失败常是多维同时命中(如收益输 hs300,Calmar 同时劣于 dca),
 * 收集所有命中项而非归到"主因",避免丢信息。语义严格对应 {@link BenchmarkCalculator#judgePassed}
 * 的取反:收益须严格 {@code >} hs300/dca(临界等于算未达标),策略 Calmar 须 {@code >=} dca Calmar。
 *
 * <p>{@link #TEST_SPLIT_TOO_SHORT} 是 test 净值不足 2 条的边界兜底
 * (基准计算对 size<2 返零收益零回撤会掩盖真因,单独拎出)。
 * {@code TRAIN_NO_VALID_CANDIDATE} 不在此枚举——train 全候选回撤 0 时编排层已单独抛异常,
 * 不进入 validate 主路径。
 *
 * <p><b>判定口径变更(ADR-0010)</b>:回撤维度从绝对值约束(策略回撤 ≤ dca 回撤)改为相对值约束
 * (策略 Calmar ≥ dca Calmar)——绝对值约束惩罚用合理风险换合理收益的策略,Calmar 比才体现
 * "回撤对得起收益"。all-in 已退出 passed 判定(收益门槛不可战胜 + 回撤约束过松),仍计算落库供展示。
 */
public enum FailureReason {
    TEST_SPLIT_TOO_SHORT,
    TEST_RETURN_BELOW_HS300,
    TEST_RETURN_BELOW_DCA,
    TEST_CALMAR_BELOW_DCA
}
