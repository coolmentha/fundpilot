package com.fundpilot.backend.strategy.service.support;

import java.math.BigDecimal;
import java.util.List;

/**
 * 寻优样本外验证的诊断结果(issue #28 诊断增强)。
 *
 * <p>把原本 {@code testPassed} 只返 boolean 丢掉的中间量保留下来,供失败时拼 message 与结构化日志,
 * 用于区分三种失败模式:
 * <ol>
 *   <li><b>真过拟合</b>:trainCalmar 高 + test 翻车(收益深负/回撤爆)</li>
 *   <li><b>门槛/单切分方差</b>:train/test 指标接近,只差一点未过某条基准</li>
 *   <li><b>regime 不匹配</b>:train/test 都一般,test 段整体难做(ADR-0007 的"正常结果")</li>
 * </ol>
 *
 * <p>{@code passed} 与 {@code reasons.isEmpty()} 等价——reasons 是唯一判定源,
 * 语义复刻 {@link BenchmarkCalculator#judgePassed}(收益严格 {@code >} hs300/dca,策略 Calmar {@code >=} dca Calmar),
 * 避免判定与诊断两个源漂移。allIn 已退出判定,仍携带供展示基金自然回撤。
 *
 * <p>纯内部诊断结构,不进 ApiResponse、不落库,只服务于日志/message 拼装。
 *
 * @param passed                test 集是否达标
 * @param bestParams            train 集选出的最优参数(诊断对象)
 * @param trainReturn           best 参数在 train 集的策略收益
 * @param trainMaxDrawdown      best 参数在 train 集的最大回撤
 * @param trainCalmar           train 风险调整收益(收益/回撤);train 回撤为 0 时 null
 * @param testStrategyReturn    best 参数在 test 集的策略收益;{@link FailureReason#TEST_SPLIT_TOO_SHORT} 时 null
 * @param testStrategyMaxDrawdown best 参数在 test 集的最大回撤;TEST_SPLIT_TOO_SHORT 时 null
 * @param testStrategyCalmar    test 策略 Calmar(收益/回撤);null 表示 +∞(零回撤);TEST_SPLIT_TOO_SHORT 时 null
 * @param dcaCalmar             test dca Calmar(收益/回撤);null 表示 +∞(零回撤);TEST_SPLIT_TOO_SHORT 时 null
 * @param hs300                 test 窗口沪深300 基准(收益基准);TEST_SPLIT_TOO_SHORT 时 null
 * @param allIn                 test 窗口 all-in 指标;判定已不使用,仅供展示基金自然回撤;TEST_SPLIT_TOO_SHORT 时 null
 * @param dca                   test 窗口 DCA 基准(收益+Calmar 基准);TEST_SPLIT_TOO_SHORT 时 null
 * @param reasons               命中的失败原因(空 = passed);多维权同时命中时收集全部
 */
public record OptimizeValidationResult(
        boolean passed,
        OptimizeParams bestParams,
        BigDecimal trainReturn,
        BigDecimal trainMaxDrawdown,
        BigDecimal trainCalmar,
        BigDecimal testStrategyReturn,
        BigDecimal testStrategyMaxDrawdown,
        BigDecimal testStrategyCalmar,
        BigDecimal dcaCalmar,
        BenchmarkMetrics hs300,
        BenchmarkMetrics allIn,
        BenchmarkMetrics dca,
        List<FailureReason> reasons) {
}
