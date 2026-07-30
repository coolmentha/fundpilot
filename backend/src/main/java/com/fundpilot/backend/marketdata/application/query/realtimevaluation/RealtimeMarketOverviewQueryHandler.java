package com.fundpilot.backend.marketdata.application.query.realtimevaluation;

import com.fundpilot.backend.marketdata.application.gateway.realtimevaluation.RealtimeMarketOverviewGateway;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RealtimeMarketOverviewQueryHandler {
    private final RealtimeMarketOverviewGateway cache;

    public List<RealtimeMarketOverviewGateway.IndexQuote> findCurrentActorIndices() {
        return cache.findCurrentActorIndices();
    }

    public RealtimeMarketOverviewGateway.Breadth findBreadth() {
        return cache.findBreadth();
    }

    public Map<String, RealtimeMarketOverviewGateway.Estimate> findEstimates(List<String> fundCodes) {
        return cache.findEstimates(fundCodes);
    }

    public List<RealtimeMarketOverviewGateway.Sector> findSectors() {
        return cache.findSectors();
    }

    public RealtimeMarketOverviewGateway.MoneyFlow findMoneyFlow() {
        return cache.findMoneyFlow();
    }
}
