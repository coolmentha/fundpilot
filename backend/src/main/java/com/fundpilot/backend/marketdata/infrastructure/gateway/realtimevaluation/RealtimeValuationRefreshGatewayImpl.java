package com.fundpilot.backend.marketdata.infrastructure.gateway.realtimevaluation;

import com.fundpilot.backend.marketdata.infrastructure.cache.realtimevaluation.MarketRealtimeCache;
import com.fundpilot.backend.marketdata.application.gateway.realtimevaluation.RealtimeValuationRefreshGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RealtimeValuationRefreshGatewayImpl implements RealtimeValuationRefreshGateway {
    private final MarketRealtimeCache cache;

    @Override public void refreshIndices() { cache.refreshIndices(); }
    @Override public void refreshAll() { cache.refreshAll(); }
    @Override public void refreshRealtimeWithoutEstimates() { cache.refreshRealtimeWithoutEstimates(); }
    @Override public void refreshFundEstimates() { cache.refreshFundEstimates(); }
    @Override public void refreshQdiiFundEstimates() { cache.refreshQdiiFundEstimates(); }
}
