package com.fundpilot.backend.market.client;

/**
 * 基金字典条目:东方财富 fundcode_search.js 解析结果。
 * [fundCode, fundName, rawType] 三元组,供 #8 FundSubTypeClassifier 做名称启发式分类。
 *
 * @param fundCode  基金代码(字符串,如 "000001")
 * @param fundName  基金名称(如 "华夏成长混合")
 * @param rawName   原始类型字符串(如 "稳健成长型"),供启发式分类用
 */
public record FundDictEntry(String fundCode, String fundName, String rawName) {
}