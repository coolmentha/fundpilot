package com.fundpilot.backend.discipline.domain.advice;

/** Discipline 建议可请求的账目方向。 */
public enum AdviceAction {
    NONE,
    BUILD,
    ADD,
    SELL;

    public boolean requiresAmount() {
        return this == BUILD || this == ADD;
    }

    public boolean requiresShares() {
        return this == SELL;
    }
}
