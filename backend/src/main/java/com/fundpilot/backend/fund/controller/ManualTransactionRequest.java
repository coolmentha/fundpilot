package com.fundpilot.backend.fund.controller;

import com.fundpilot.backend.fund.enums.FundTransactionSource;

import java.math.BigDecimal;

/**
 * 手动录入交易请求(issue #18 手动交易):支持加仓/减仓/转入/转出/定投五类,绕过信号。
 * <p>买入类(INCREASE/TRANSFER_IN/INVEST)填 {@code amount};卖出类(DECREASE/TRANSFER_OUT)填 {@code shares}。
 * 另一侧(买入的 shares / 卖出的 amount)由 NavConfirmJob 当晚净值确认后回填。
 *
 * @param source 交易来源(五值之一)
 * @param amount 金额(买入类必填)
 * @param shares 份额(卖出类必填)
 */
public record ManualTransactionRequest(
        FundTransactionSource source,
        BigDecimal amount,
        BigDecimal shares) {
}
