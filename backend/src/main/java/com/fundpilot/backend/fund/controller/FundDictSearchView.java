package com.fundpilot.backend.fund.controller;

import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;

/**
 * 基金字典搜索结果(ADR-0005):搜索框候选列表的一行,携带选中后一次性回填所需的全部字段。
 *
 * @param fundCode           基金代码(如 510300)
 * @param fundName           基金名称(如 易方达沪深300ETF联接A)
 * @param fundSubType        数据源维度分类(ETF/INDEX/INDEX_ENHANCED/ACTIVE)
 * @param fundCategory       策略参数维度分类(宽基/行业/主动/混合)
 * @param benchmarkIndexCode 跟踪/基准指数代码(如 000300.SH)
 */
public record FundDictSearchView(
        String fundCode,
        String fundName,
        FundSubType fundSubType,
        FundCategory fundCategory,
        String benchmarkIndexCode) {
}
