package com.fundpilot.backend.market.service;

import com.fundpilot.backend.market.client.FundEstimateSnapshot;

public record FundEstimateResult(EstimateStatus status, FundEstimateSnapshot snapshot) {

    public static FundEstimateResult available(FundEstimateSnapshot snapshot) {
        return new FundEstimateResult(EstimateStatus.AVAILABLE, snapshot);
    }

    public static FundEstimateResult unavailable() {
        return new FundEstimateResult(EstimateStatus.UNAVAILABLE, null);
    }

    public static FundEstimateResult failed(EstimateStatus status) {
        if (!status.isFailure()) {
            throw new IllegalArgumentException("非失败状态: " + status);
        }
        return new FundEstimateResult(status, null);
    }
}
