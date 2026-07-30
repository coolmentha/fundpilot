package com.fundpilot.backend.marketdata.adapter.event.indicatorrefresh;

import com.fundpilot.backend.marketdata.application.command.indicatorrefresh.MarketIndicatorRefreshCommandHandler;
import com.fundpilot.backend.portfolio.application.event.portfoliofund.PortfolioFundTrackedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class PortfolioFundTrackedMarketDataListener {
    private final MarketIndicatorRefreshCommandHandler commands;

    @ApplicationModuleListener
    public void onPortfolioFundTracked(PortfolioFundTrackedEvent event) {
        try {
            commands.refreshOneForPortfolioFund(event.portfolioFundId());
        } catch (RuntimeException exception) {
            log.warn("组合基金 {} 跟踪后异步拉取历史净值失败: {}", event.portfolioFundId(), exception.getMessage());
        }
    }
}
