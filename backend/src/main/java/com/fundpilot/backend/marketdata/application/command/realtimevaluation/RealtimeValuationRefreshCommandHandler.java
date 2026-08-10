package com.fundpilot.backend.marketdata.application.command.realtimevaluation;

import com.fundpilot.backend.marketdata.application.gateway.realtimevaluation.RealtimeValuationRefreshGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RealtimeValuationRefreshCommandHandler {
    private final RealtimeValuationRefreshGateway cache;

    public void refreshIndices() { cache.refreshIndices(); }
    public void refreshAll() { cache.refreshAll(); }
    public void refreshRealtimeWithoutEstimates() { cache.refreshRealtimeWithoutEstimates(); }
    public void refreshFundEstimates() { cache.refreshFundEstimates(); }
    public void refreshQdiiFundEstimates() { cache.refreshQdiiFundEstimates(); }
}
