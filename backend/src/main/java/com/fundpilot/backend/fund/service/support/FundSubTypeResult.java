package com.fundpilot.backend.fund.service.support;

import com.fundpilot.backend.fund.enums.FundSubType;

/**
 * {@link FundSubTypeClassifier#classify(String)} 的返回值。
 *
 * @param fundSubType        识别出的基金子类型(ETF / INDEX / INDEX_ENHANCED / ACTIVE)
 * @param benchmarkIndexCode 业绩比较基准指数代码(如 {@code "000300.SH"}),识别不出时为 {@code null}
 */
public record FundSubTypeResult(FundSubType fundSubType, String benchmarkIndexCode) {
}
