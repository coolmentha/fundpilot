package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import java.math.BigDecimal;
import java.util.List;

/** 同花顺分钟估值曲线；仅用于基金详情当日分时展示。 */
public record FundIntradayChart(String estimateDate, String baseNavDate, BigDecimal baseNav, List<Point> points,
                                List<TradingSession> tradingSessions) {

    public FundIntradayChart {
        points = points == null ? List.of() : List.copyOf(points);
        tradingSessions = tradingSessions == null ? List.of() : List.copyOf(tradingSessions);
    }

    public FundIntradayChart(String estimateDate, String baseNavDate, BigDecimal baseNav, List<Point> points) {
        this(estimateDate, baseNavDate, baseNav, points, List.of());
    }

    public record Point(String time, BigDecimal nav) {
    }

    public record TradingSession(String start, String end) {
    }
}
