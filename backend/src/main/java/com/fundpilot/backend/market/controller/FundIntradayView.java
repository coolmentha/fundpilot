package com.fundpilot.backend.market.controller;

import com.fundpilot.backend.market.client.FundIntradayChart;

import java.math.BigDecimal;
import java.util.List;

/** 基金详情当日分时图的只读视图。 */
public record FundIntradayView(String estimateDate, BigDecimal baseNav, List<Point> points) {

    public static FundIntradayView from(FundIntradayChart chart) {
        if (chart == null) {
            return null;
        }
        return new FundIntradayView(chart.estimateDate(), chart.baseNav(), chart.points().stream()
                .map(point -> new Point(point.time(), point.nav())).toList());
    }

    public record Point(String time, BigDecimal nav) {
    }
}
