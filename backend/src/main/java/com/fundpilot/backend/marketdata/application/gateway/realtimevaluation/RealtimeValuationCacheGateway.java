package com.fundpilot.backend.marketdata.application.gateway.realtimevaluation;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface RealtimeValuationCacheGateway {
    Map<String, Valuation> findByFundCodes(Collection<String> fundCodes);
    Optional<Intraday> findIntraday(String fundCode);

    record Valuation(String fundCode, BigDecimal estimatedChangePct, String estimateTime,
                     String baseNavDate, String status) {}
    record Intraday(String estimateDate, BigDecimal baseNav, java.util.List<Point> points,
                    java.util.List<TradingSession> tradingSessions) {
        public Intraday(String estimateDate, BigDecimal baseNav, java.util.List<Point> points) {
            this(estimateDate, baseNav, points, java.util.List.of());
        }
    }
    record Point(String time, BigDecimal nav) {}
    record TradingSession(String start, String end) {}
}
