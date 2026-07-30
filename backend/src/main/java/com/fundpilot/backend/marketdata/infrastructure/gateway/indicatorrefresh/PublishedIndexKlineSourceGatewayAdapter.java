package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import com.fundpilot.backend.marketdata.application.gateway.indicatorrefresh.PublishedIndexKlineSourceGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PublishedIndexKlineSourceGatewayAdapter implements PublishedIndexKlineSourceGateway {
    private final IndexKlineSource source;

    @Override
    public IndexKline fetch(String secid, String limit) {
        var kline = source.fetchIndexKline(secid, limit);
        return new IndexKline(kline.bars().stream().map(bar -> new Bar(bar.date(), bar.open(), bar.high(),
                bar.low(), bar.close(), bar.volume())).toList());
    }
}
