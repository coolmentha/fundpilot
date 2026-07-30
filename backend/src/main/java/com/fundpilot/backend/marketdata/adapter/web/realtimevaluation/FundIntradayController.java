package com.fundpilot.backend.marketdata.adapter.web.realtimevaluation;

import com.fundpilot.backend.marketdata.application.query.realtimevaluation.RealtimeValuationQueryHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FundIntradayController {
    private final RealtimeValuationQueryHandler queries;

    @GetMapping("/api/funds/{fundId}/intraday")
    public ApiResponse<FundIntradayView> intraday(@PathVariable long fundId) {
        return ApiResponse.ok(queries.findIntraday(fundId).map(FundIntradayView::from).orElse(null));
    }

    @GetMapping("/api/portfolio-funds/{portfolioFundId}/intraday")
    public ApiResponse<FundIntradayView> portfolioFundIntraday(@PathVariable long portfolioFundId) {
        return ApiResponse.ok(queries.findIntradayForPortfolioFund(portfolioFundId)
                .map(FundIntradayView::from).orElse(null));
    }

    public record FundIntradayView(String estimateDate, BigDecimal baseNav, List<Point> points) {
        static FundIntradayView from(RealtimeValuationQueryHandler.IntradayResult value) {
            return new FundIntradayView(value.estimateDate(), value.baseNav(), value.points().stream()
                    .map(point -> new Point(point.time(), point.nav())).toList());
        }
    }

    public record Point(String time, BigDecimal nav) {}
}
