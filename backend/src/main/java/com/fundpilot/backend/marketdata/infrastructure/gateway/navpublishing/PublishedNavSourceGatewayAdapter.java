package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import com.fundpilot.backend.marketdata.application.gateway.navpublishing.PublishedNavSourceGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
class PublishedNavSourceGatewayAdapter implements PublishedNavSourceGateway {
    private final MarketDataSourceChain sources;

    @Override
    public List<NavSnapshot> fetchHistory(String fundCode) {
        return sources.fetchNavHistory(fundCode).stream()
                .map(nav -> new NavSnapshot(nav.navDate(), nav.nav(), nav.accumulatedNav()))
                .toList();
    }
}
