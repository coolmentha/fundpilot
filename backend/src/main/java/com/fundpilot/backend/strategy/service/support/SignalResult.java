package com.fundpilot.backend.strategy.service.support;

import com.fundpilot.backend.signal.enums.SignalReason;
import com.fundpilot.backend.signal.enums.SignalType;

import java.util.List;

/**
 * 信号引擎输出(ADR-0015 重写):evaluateSignal 的返回值,落入 {@code SignalLogEntity}。
 * <p>定投移动止盈只有 NONE/SELL 两态;SELL 携带建议卖出份额(供 SignalOperationService 写 DECREASE 交易)。
 *
 * @param signalType   信号类型(NONE/SELL)
 * @param sellShares  SELL 时的建议卖出份额(当前持仓 × sellRatio);NONE 可 null
 * @param reason      触发原因(枚举,详见 {@link SignalReason})
 * @param warnings    强提示列表
 */
public record SignalResult(
        SignalType signalType,
        java.math.BigDecimal sellShares,
        SignalReason reason,
        List<com.fundpilot.backend.signal.enums.SignalWarningValue> warnings) {

    /** 快速构造 NONE 结果。 */
    public static SignalResult none(SignalReason reason) {
        return new SignalResult(SignalType.NONE, null, reason, List.of());
    }
}