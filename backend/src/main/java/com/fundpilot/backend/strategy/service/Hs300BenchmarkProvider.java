package com.fundpilot.backend.strategy.service;

import com.fundpilot.backend.strategy.service.support.BenchmarkMetrics;

import java.time.Instant;

/**
 * 沪深300 同期基准线提供者(issue #11):按回测窗口拉沪深300 指数日 K,算同期收益与最大回撤。
 * <p>独立成接口便于 {@code DefaultStrategyBacktestService} 测试时 Mock,避免真实网络调用。
 */
public interface Hs300BenchmarkProvider {

    /**
     * @param start 回测起始日期(含,UTC)
     * @param end   回测结束日期(含,UTC)
     * @return 沪深300 同期收益 + 最大回撤;无可用数据时返回零指标
     */
    BenchmarkMetrics fetch(Instant start, Instant end);
}
