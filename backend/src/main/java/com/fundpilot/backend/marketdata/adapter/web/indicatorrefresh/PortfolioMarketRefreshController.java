package com.fundpilot.backend.marketdata.adapter.web.indicatorrefresh;

import com.fundpilot.backend.marketdata.application.command.indicatorrefresh.PortfolioMarketRefreshCommandHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PortfolioMarketRefreshController {
    private final PortfolioMarketRefreshCommandHandler commands;

    @PostMapping("/api/portfolio-funds/{portfolioFundId}/market-data/refresh")
    public ApiResponse<RefreshView> refresh(@PathVariable long portfolioFundId) {
        return ApiResponse.ok(RefreshView.from(commands.refresh(portfolioFundId)));
    }

    public record RefreshView(long portfolioFundId) {
        static RefreshView from(PortfolioMarketRefreshCommandHandler.Result result) {
            return new RefreshView(result.portfolioFundId());
        }
    }
}
