package com.fundpilot.backend.fund.service.support;

import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;

/**
 * 基金类型识别统一结果:一次返回 fundSubType + fundCategory + benchmarkIndexCode。
 * <p>落库 {@code fund_dict} 时由 {@link FundTypeClassifier#classify(String)} 一次算完并缓存,
 * 搜索接口直接返回缓存值,避免运行时对同一条目重复跑启发式。详见 CONTEXT.md「基金类型自动识别」。
 *
 * @param fundSubType        数据源维度子类型(ETF/INDEX/INDEX_ENHANCED/ACTIVE)
 * @param fundCategory       策略参数维度类型(宽基/行业/主动/混合)
 * @param benchmarkIndexCode 业绩比较基准指数代码(如 {@code "000300.SH"}),识别不出时为 {@code null}
 */
public record FundTypeClassification(FundSubType fundSubType, FundCategory fundCategory,
                                     String benchmarkIndexCode) {
}
