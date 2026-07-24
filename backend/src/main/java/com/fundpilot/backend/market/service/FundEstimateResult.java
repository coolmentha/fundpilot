package com.fundpilot.backend.market.service;

import com.fundpilot.backend.market.client.FundEstimateSnapshot;
import com.fundpilot.backend.market.client.FundIntradayChart;

public record FundEstimateResult(EstimateStatus status, FundEstimateSnapshot snapshot, FundIntradayChart intradayChart) {

    public static FundEstimateResult available(FundEstimateSnapshot snapshot) {
        return new FundEstimateResult(EstimateStatus.AVAILABLE, snapshot, null);
    }

    public static FundEstimateResult available(FundEstimateSnapshot snapshot, FundIntradayChart intradayChart) {
        return new FundEstimateResult(EstimateStatus.AVAILABLE, snapshot, intradayChart);
    }

    public static FundEstimateResult unavailable() {
        return new FundEstimateResult(EstimateStatus.UNAVAILABLE, null, null);
    }

    public static FundEstimateResult failed(EstimateStatus status) {
        if (!status.isFailure()) {
            throw new IllegalArgumentException("非失败状态: " + status);
        }
        return new FundEstimateResult(status, null, null);
    }
}
