package com.fundpilot.backend.signal.controller;

import com.fundpilot.backend.signal.entity.SignalLogEntity;
import com.fundpilot.backend.signal.enums.SignalReason;
import com.fundpilot.backend.signal.enums.SignalType;
import com.fundpilot.backend.signal.valueobject.Measure;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 信号日志视图 DTO(ADR-0015):只含业务字段,关联对象只取 id。定投移动止盈只有 NONE/SELL。
 *
 * @param id              信号日志 ID
 * @param fundId          基金 ID
 * @param fundStrategyId  策略 ID
 * @param signalDate      信号生成时间
 * @param triggerNav      触发净值
 * @param signalType      信号类型(NONE/SELL)
 * @param suggestedMeasure 建议量值(SELL 存份额)
 * @param reason          触发原因
 * @param warnings        强提示(逗号分隔)
 */
public record SignalLogView(
        Long id,
        Long fundId,
        Long fundStrategyId,
        Instant signalDate,
        BigDecimal triggerNav,
        SignalType signalType,
        Measure suggestedMeasure,
        SignalReason reason,
        String warnings) {

    public static SignalLogView from(SignalLogEntity log) {
        return new SignalLogView(
                log.getId(),
                log.getFundEntity() != null ? log.getFundEntity().getId() : null,
                log.getFundStrategyEntity() != null ? log.getFundStrategyEntity().getId() : null,
                log.getSignalDate(),
                log.getTriggerNav(),
                log.getSignalType(),
                log.getSuggestedMeasure(),
                log.getReason(),
                log.getWarnings());
    }
}