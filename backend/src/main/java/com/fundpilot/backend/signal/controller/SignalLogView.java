package com.fundpilot.backend.signal.controller;

import com.fundpilot.backend.signal.entity.SignalLogEntity;
import com.fundpilot.backend.signal.enums.SignalReason;
import com.fundpilot.backend.signal.enums.SignalActionStatus;
import com.fundpilot.backend.signal.enums.SignalType;
import com.fundpilot.backend.signal.valueobject.Measure;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 信号日志视图 DTO(issue #16):只含业务字段,关联对象只取 id,不暴露 Entity 内部字段。
 *
 * @param id                    信号日志 ID
 * @param fundId                基金 ID
 * @param fundStrategyId        策略 ID
 * @param signalDate            信号生成时间
 * @param triggerNav            触发净值
 * @param triggerTier           触发档位(1~4)
 * @param coefficient           调节系数
 * @param signalType            信号类型(NONE/BUILD/ADD/SELL)
 * @param suggestedMeasure      建议量值(BUILD/ADD 存金额,SELL 存份额)
 * @param reason                触发原因
 * @param warnings              强提示(逗号分隔)
 * @param hardConstraintBreaches 硬约束违反(逗号分隔)
 */
public record SignalLogView(
        Long id,
        Long fundId,
        Long fundStrategyId,
        Instant signalDate,
        BigDecimal triggerNav,
        Integer triggerTier,
        BigDecimal coefficient,
        SignalType signalType,
        Measure suggestedMeasure,
        SignalReason reason,
        String warnings,
        String hardConstraintBreaches,
        SignalActionStatus actionStatus,
        Instant ignoredDate,
        Long relatedTransactionId,
        FundTransactionStatus relatedTransactionStatus) {

    public static SignalLogView from(SignalLogEntity log, SignalActionStatus actionStatus) {
        return new SignalLogView(
                log.getId(),
                log.getFundEntity() != null ? log.getFundEntity().getId() : null,
                log.getFundStrategyEntity() != null ? log.getFundStrategyEntity().getId() : null,
                log.getSignalDate(),
                log.getTriggerNav(),
                log.getTriggerTier(),
                log.getCoefficient(),
                log.getSignalType(),
                log.getSuggestedMeasure(),
                log.getReason(),
                log.getWarnings(),
                log.getHardConstraintBreaches(),
                actionStatus,
                log.getIgnoredDate(), null, null);
    }

    public SignalLogView withTransaction(Long transactionId, FundTransactionStatus transactionStatus) {
        return new SignalLogView(id, fundId, fundStrategyId, signalDate, triggerNav, triggerTier, coefficient,
                signalType, suggestedMeasure, reason, warnings, hardConstraintBreaches, actionStatus, ignoredDate,
                transactionId, transactionStatus);
    }
}
