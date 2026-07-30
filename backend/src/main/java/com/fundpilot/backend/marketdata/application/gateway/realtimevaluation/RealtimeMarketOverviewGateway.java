package com.fundpilot.backend.marketdata.application.gateway.realtimevaluation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Reads the realtime market-workbench cache in MarketData language. */
public interface RealtimeMarketOverviewGateway {
    List<IndexQuote> findCurrentActorIndices();
    Breadth findBreadth();
    Map<String, Estimate> findEstimates(List<String> fundCodes);
    List<Sector> findSectors();
    MoneyFlow findMoneyFlow();

    record IndexQuote(String secid, String name, BigDecimal currentPrice, BigDecimal changeAmount,
                      BigDecimal changePct, BigDecimal turnover) {}
    record Breadth(int risingCount, int fallingCount, Integer limitUpCount, Integer limitDownCount) {}
    record Estimate(BigDecimal estimatedChangePct, String estimateTime, String baseNavDate) {}
    record Sector(String sectorName, BigDecimal changePct, BigDecimal turnover, BigDecimal mainforceNet) {}
    record MoneyFlow(BigDecimal northboundNet, Instant snapshotTime) {}
}
