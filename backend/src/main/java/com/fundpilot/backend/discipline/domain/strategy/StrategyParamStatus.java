package com.fundpilot.backend.discipline.domain.strategy;

/**
 * 策略参数状态机(CONTEXT.md「策略状态机 StrategyParamStatus」):
 * {@code PENDING_CALIBRATION --activate--> EFFECTIVE --retire--> PENDING_CALIBRATION}。
 * {@code CALIBRATED}/{@code CALIBRATION_FAILED} 仅为存量数据兼容保留,不再产生新流转(ADR-0015)。
 */
public enum StrategyParamStatus {
    PENDING_CALIBRATION,
    EFFECTIVE,
    CALIBRATED,
    CALIBRATION_FAILED
}
