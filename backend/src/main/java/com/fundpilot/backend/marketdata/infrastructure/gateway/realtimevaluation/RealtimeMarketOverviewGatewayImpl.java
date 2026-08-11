package com.fundpilot.backend.marketdata.infrastructure.gateway.realtimevaluation;

import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi;
import com.fundpilot.backend.marketdata.adapter.api.watchedindex.WatchedIndicesApi;
import com.fundpilot.backend.marketdata.application.gateway.realtimevaluation.RealtimeMarketOverviewGateway;
import com.fundpilot.backend.marketdata.infrastructure.cache.realtimevaluation.MarketRealtimeCache;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RealtimeMarketOverviewGatewayImpl implements RealtimeMarketOverviewGateway {
    private final MarketRealtimeCache cache;
    private final CurrentActorApi actors;
    private final WatchedIndicesApi watchedIndices;

    @Override
    public List<IndexQuote> findCurrentActorIndices() {
        return cache.getIndices(watchedIndices.findByOwner(actors.userId())).stream()
                .map(value -> new IndexQuote(value.secid(), value.name(), value.currentPrice(),
                        value.changeAmount(), value.changePct(), value.turnover())).toList();
    }

    @Override
    public Breadth findBreadth() {
        var value = cache.getBreadth();
        return value == null ? null : new Breadth(value.risingCount(), value.fallingCount(),
                value.flatCount(), value.limitUpCount(), value.limitDownCount());
    }

    @Override
    public Map<String, Estimate> findEstimates(List<String> fundCodes) {
        return cache.getEstimates(fundCodes).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> new Estimate(entry.getValue().estimatedChangePct(), entry.getValue().estimateTime(),
                        entry.getValue().baseNavDate())));
    }

    @Override
    public List<Sector> findSectors() {
        return cache.getSectors().stream().map(value -> new Sector(value.sectorName(), value.changePct(),
                value.turnover(), value.mainforceNet())).toList();
    }

    @Override
    public MoneyFlow findMoneyFlow() {
        var value = cache.getMoneyFlow();
        return value == null ? null : new MoneyFlow(value.northboundNet(), value.snapshotTime());
    }

    @Override
    public java.time.Instant findUpdatedAt() {
        return cache.getMarketUpdatedAt();
    }
}
