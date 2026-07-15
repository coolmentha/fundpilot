package com.fundpilot.backend.market.service;

public enum EstimateStatus {
    NOT_ATTEMPTED,
    AVAILABLE,
    UNAVAILABLE,
    STALE,
    TIMEOUT,
    PARSE_ERROR;

    public boolean isFailure() {
        return this == TIMEOUT || this == PARSE_ERROR;
    }
}
