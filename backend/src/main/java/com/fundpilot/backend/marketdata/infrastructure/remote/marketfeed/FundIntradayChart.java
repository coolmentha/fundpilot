package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import java.math.BigDecimal;
import java.util.List;

/** 同花顺分钟估值曲线；仅用于基金详情当日分时展示。 */
public record FundIntradayChart(String estimateDate, String baseNavDate, BigDecimal baseNav, List<Point> points) {

    public record Point(String time, BigDecimal nav) {
    }
}
