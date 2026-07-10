package com.fundpilot.backend.fund.controller;

import com.fundpilot.backend.fund.enums.FundTransactionSource;

import java.math.BigDecimal;

/**
 * 手动录入交易请求(issue #18 手动交易):支持加仓/减仓/转入/转出/定投/调增/调减七类,绕过信号。
 * <p>买入类(INCREASE/TRANSFER_IN/INVEST)填 {@code amount};卖出类(DECREASE/TRANSFER_OUT)填 {@code shares}。
 * 另一侧(买入的 shares / 卖出的 amount)由 NavConfirmJob 当晚净值确认后回填。
 *
 * <p>基金转换(task 07-08):{@code source=TRANSFER_OUT} 时填 {@code targetFundId},
 * 后端创建转出+转入两条互指交易(relatedTransaction 双向 set),确认时先算转出净金额->回填转入 amount->算转入份额。
 * {@code targetFundId} 为空表示纯转出(跨公司超级转换/独立记录),走原单条逻辑。
 *
 * @param source       交易来源(七值之一)
 * @param amount       金额(买入类必填)
 * @param shares       份额(卖出类必填)
 * @param targetFundId 转入基金 ID(仅 source=TRANSFER_OUT 且转换模式时填;可空)
 */
public record ManualTransactionRequest(
        FundTransactionSource source,
        BigDecimal amount,
        BigDecimal shares,
        Long targetFundId) {
}
