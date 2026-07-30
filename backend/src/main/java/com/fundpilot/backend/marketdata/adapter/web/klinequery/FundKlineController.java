package com.fundpilot.backend.marketdata.adapter.web.klinequery;

import com.fundpilot.backend.marketdata.application.query.klinequery.KlineQueryHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FundKlineController {
    private final KlineQueryHandler queries;

    @GetMapping("/api/funds/{fundId}/kline")
    public ApiResponse<KlineView> kline(@PathVariable long fundId,
                                        @RequestParam(name = "period", defaultValue = "daily") String period) {
        return ApiResponse.ok(KlineView.from(queries.getKline(fundId, period)));
    }

    @GetMapping("/api/portfolio-funds/{portfolioFundId}/kline")
    public ApiResponse<KlineView> portfolioFundKline(@PathVariable long portfolioFundId,
                                                     @RequestParam(name = "period", defaultValue = "daily") String period) {
        return ApiResponse.ok(KlineView.from(queries.getKlineForPortfolioFund(portfolioFundId, period)));
    }

    public record KlineView(String chartType, String benchmark, List<Bar> bars) {
        static KlineView from(KlineQueryHandler.Kline value) {
            return new KlineView(value.chartType(), value.benchmark(), value.bars().stream()
                    .map(bar -> new Bar(bar.date(), bar.open(), bar.close(), bar.high(), bar.low(), bar.volume()))
                    .toList());
        }
    }

    public record Bar(Instant date, BigDecimal open, BigDecimal close, BigDecimal high,
                      BigDecimal low, long volume) {}
}
