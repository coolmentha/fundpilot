package com.fundpilot.backend.marketdata.application.gateway.realtimevaluation;

public interface RealtimeValuationRefreshGateway {
    void refreshIndices();
    void refreshAll();
    void refreshFundEstimates();
}
