package com.fundpilot.backend.productcatalog.application.gateway.benchmark;

import java.util.Optional;

/**
 * 解析基金跟踪的基准指数(issue: 创建时准确识别跟踪标的)。
 * <p>数据源为东方财富基金详情接口返回的 INDEXCODE(如 008888 → 980017.SZ 国证半导体芯片指数)。
 * 仅用于单只基金创建(ensure),全量目录同步仍走关键词分类,避免批量外部调用。
 * <p>实现须容忍外部接口失败:无跟踪标的/解析失败/网络异常一律返回 {@link Optional#empty()},
 * 由调用方回退到 {@code ProductClassifier} 关键词分类。
 */
public interface BenchmarkIndexResolver {

    /** 返回基金跟踪的基准指数代码(格式 "980017.SZ"),无则 empty。 */
    Optional<String> resolve(String fundCode);
}
