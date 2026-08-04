package com.fundpilot.backend.marketdata.application.query.realtimevaluation;

import com.fundpilot.backend.marketdata.application.gateway.realtimevaluation.RealtimeValuationCacheGateway;
import com.fundpilot.backend.marketdata.application.gateway.portfoliofund.OwnedFundProductGateway;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RealtimeValuationQueryHandler {
    private final RealtimeValuationCacheGateway cache;
    private final OwnedFundProductGateway products;

    public List<ValuationResult> findByFundCodes(Collection<String> fundCodes) {
        return cache.findByFundCodes(fundCodes).values().stream()
                .map(value -> new ValuationResult(value.fundCode(), value.estimatedChangePct(),
                        value.estimateTime(), value.baseNavDate(), value.status())).toList();
    }

    public Optional<IntradayResult> findIntraday(long legacyFundId) {
        return products.findOwned(legacyFundId).flatMap(product -> cache.findIntraday(product.fundCode()))
                .map(RealtimeValuationQueryHandler::toIntradayResult);
    }

    public Optional<IntradayResult> findIntradayForPortfolioFund(long portfolioFundId) {
        return products.findOwnedByPortfolioFundId(portfolioFundId)
                .flatMap(product -> cache.findIntraday(product.fundCode()))
                .map(RealtimeValuationQueryHandler::toIntradayResult);
    }

    private static IntradayResult toIntradayResult(RealtimeValuationCacheGateway.Intraday value) {
        return new IntradayResult(value.estimateDate(), value.baseNav(), value.points().stream()
                .map(point -> new IntradayPoint(point.time(), point.nav())).toList(),
                value.tradingSessions().stream()
                        .map(session -> new IntradaySession(session.start(), session.end())).toList());
    }

    public record ValuationResult(String fundCode, java.math.BigDecimal estimatedChangePct,
                                  String estimateTime, String baseNavDate, String status) {}
    public record IntradayResult(String estimateDate, java.math.BigDecimal baseNav, List<IntradayPoint> points,
                                 List<IntradaySession> tradingSessions) {}
    public record IntradayPoint(String time, java.math.BigDecimal nav) {}
    public record IntradaySession(String start, String end) {}
}
