package com.fundpilot.backend.strategy.service.support;

import com.fundpilot.backend.fund.service.support.Breach;
import com.fundpilot.backend.signal.enums.SignalReason;
import com.fundpilot.backend.signal.enums.SignalType;
import com.fundpilot.backend.signal.enums.SignalWarningValue;
import com.fundpilot.backend.signal.valueobject.Measure;

import java.math.BigDecimal;
import java.util.List;

/**
 * 信号引擎输出(issue #12):evaluateSignal 的返回值,后续由 #13 SignalGenerationJob 落入 {@code SignalLogEntity}。
 *
 * @param signalType             信号类型(NONE/BUILD/ADD/SELL)
 * @param triggerTier            触发档位(1~4),BUILD/SELL-移动止盈 填,其他可 null
 * @param coefficient            调节系数(ADD 时填,BUILD 固定 1.0,其他可 null)
 * @param suggestedMeasure       建议量值(BUILD/ADD 存金额,SELL 存份额),NONE 可 null
 * @param reason                 触发原因(枚举,详见 {@link SignalReason})
 * @param warnings               强提示列表(详见 {@link SignalWarningValue})
 * @param hardConstraintBreaches 硬约束违反记录(空=通过)
 */
public record SignalResult(
        SignalType signalType,
        Integer triggerTier,
        BigDecimal coefficient,
        Measure suggestedMeasure,
        SignalReason reason,
        List<SignalWarningValue> warnings,
        List<Breach> hardConstraintBreaches) {

    /** 快速构造 NONE 结果。 */
    public static SignalResult none(SignalReason reason) {
        return new SignalResult(SignalType.NONE, null, null, null, reason, List.of(), List.of());
    }
}
