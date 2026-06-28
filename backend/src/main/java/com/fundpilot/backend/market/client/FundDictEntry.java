package com.fundpilot.backend.market.client;

/**
 * 基金字典条目:东方财富 fundcode_search.js 解析结果。
 * 真实响应是 5 元组 {@code [fundCode, 拼音缩写, fundName(中文), 类型描述, 拼音全称]},
 * 本类取 {@code [0]} 代码、{@code [2]} 中文名、{@code [3]} 类型描述。
 *
 * @param fundCode  基金代码(字符串,如 "000001")
 * @param fundName  基金名称(如 "华夏成长混合")
 * @param rawName   原始类型描述(如 "混合型-灵活"/"指数型-股票"/"债券型-混合二级"),供启发式分类用
 */
public record FundDictEntry(String fundCode, String fundName, String rawName) {
}