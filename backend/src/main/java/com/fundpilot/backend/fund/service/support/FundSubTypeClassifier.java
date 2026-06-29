package com.fundpilot.backend.fund.service.support;

import com.fundpilot.backend.fund.enums.FundSubType;

import java.util.Optional;

/**
 * 基金子类型启发式分类器(issue #8):根据基金名称识别 {@link FundSubType} 和 {@code benchmarkIndexCode}。
 *
 * <h3>识别规则(按优先级)</h3>
 * <ol>
 *   <li>名称含「ETF」→ {@link FundSubType#ETF};从名称提取跟踪指数</li>
 *   <li>名称含「指数增强」或「增强」→ {@link FundSubType#INDEX_ENHANCED};从名称提取跟踪指数</li>
 *   <li>名称含「指数」或命中 {@link BenchmarkIndexTable} 指数关键词 → {@link FundSubType#INDEX};提取指数代码</li>
 *   <li>兜底 → {@link FundSubType#ACTIVE};默认填沪深300 {@code 000300.SH}(CONTEXT.md 明确,逻辑止损不使用)</li>
 * </ol>
 *
 * <h3>benchmarkIndexCode 填充策略</h3>
 * <ul>
 *   <li>ETF / INDEX / INDEX_ENHANCED:命中 {@link BenchmarkIndexTable} 则填,否则留 {@code null}(用户建仓时手动补)</li>
 *   <li>ACTIVE:固定填 {@code 000300.SH}</li>
 * </ul>
 *
 * <p>本期范围:仅方法 A(名称启发式) + 兜底 C。方法 B(持仓股票与指数成分股重合度反推)留将来。
 */
public final class FundSubTypeClassifier {

    /** ACTIVE 类型默认业绩比较基准——沪深300(CONTEXT.md 明确,逻辑止损不使用)。 */
    public static final String ACTIVE_DEFAULT_BENCHMARK = "000300.SH";

    private FundSubTypeClassifier() {
    }

    /**
     * 按基金名称启发式分类。
     *
     * @param fundName 基金名称(如「易方达沪深300ETF联接A」)
     * @return 分类结果(fundSubType + benchmarkIndexCode);入参为空时兜底 ACTIVE + 默认基准
     */
    public static FundSubTypeResult classify(String fundName) {
        if (fundName == null || fundName.isBlank()) {
            return new FundSubTypeResult(FundSubType.ACTIVE, ACTIVE_DEFAULT_BENCHMARK);
        }
        Optional<String> indexCode = BenchmarkIndexTable.lookup(fundName);

        // 规则 1:ETF 优先级最高
        if (fundName.contains("ETF")) {
            return new FundSubTypeResult(FundSubType.ETF, indexCode.orElse(null));
        }
        // 规则 2:指数增强
        if (fundName.contains("指数增强") || fundName.contains("增强")) {
            return new FundSubTypeResult(FundSubType.INDEX_ENHANCED, indexCode.orElse(null));
        }
        // 规则 3:指数(含「指数」字样或命中指数关键词)
        if (fundName.contains("指数") || indexCode.isPresent()) {
            return new FundSubTypeResult(FundSubType.INDEX, indexCode.orElse(null));
        }
        // 规则 4:兜底 ACTIVE
        return new FundSubTypeResult(FundSubType.ACTIVE, ACTIVE_DEFAULT_BENCHMARK);
    }
}
