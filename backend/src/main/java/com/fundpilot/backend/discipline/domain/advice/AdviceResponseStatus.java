package com.fundpilot.backend.discipline.domain.advice;

/** 建议回应生命周期；接受建议先创建 PENDING 账目，确认后才成为 RESPONDED。 */
public enum AdviceResponseStatus {
    PENDING,
    RESPONDED,
    IGNORED
}
