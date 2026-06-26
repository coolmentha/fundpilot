package com.fundpilot.backend.signal.controller;

import jakarta.validation.constraints.NotNull;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;

/**
 * 用户确认信号操作的请求(issue #14):用户回应 SignalLog 的统一入口。
 * <p>
 * <ul>
 *   <li>{@code actualAmount} —— BUILD/ADD 必填,用户实际下单金额(买入下单时已知金额,份额等净值回填)</li>
 *   <li>{@code actualShares} —— SELL 必填,用户实际卖出份额(卖出下单时已知份额,金额等净值回填)</li>
 * </ul>
 * <p>override 不留痕:与 SignalLog.suggestedMeasure.value 不同时,直接存 actual 值,不存 diff。
 *
 * @param signalLogId 对应的 SignalLog 主键(由 path fundId 校验归属)
 */
public record ConfirmOperationRequest(
        @NotNull Long signalLogId,
        @Nullable BigDecimal actualAmount,
        @Nullable BigDecimal actualShares) {
}
