package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import com.fundpilot.backend.marketdata.application.gateway.klinequery.IndexKlineSourceGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
class IndexKlineSourceGatewayAdapter implements IndexKlineSourceGateway {
    private final IndexKlineSource source;

    @Override
    public List<Bar> fetch(String secid, String period, String limit) {
        return source.fetchIndexKlineWithPeriod(secid, period, limit).bars().stream().map(bar ->
                new Bar(bar.date(), bar.open(), bar.high(), bar.low(), bar.close(), bar.volume())).toList();
    }
}
