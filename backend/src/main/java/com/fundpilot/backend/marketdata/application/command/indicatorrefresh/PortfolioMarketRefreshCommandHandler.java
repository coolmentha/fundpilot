package com.fundpilot.backend.marketdata.application.command.indicatorrefresh;

import com.fundpilot.backend.marketdata.application.gateway.portfoliofund.OwnedFundProductGateway;
import com.fundpilot.backend.platform.web.error.BusinessException;
import com.fundpilot.backend.platform.web.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioMarketRefreshCommandHandler {
    private final OwnedFundProductGateway products;
    private final MarketIndicatorRefreshCommandHandler refresh;

    // External fetches intentionally run outside a transaction; published market facts own their writes.
    public Result refresh(long portfolioFundId) {
        products.findOwnedByPortfolioFundId(portfolioFundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND, "组合基金不存在或已作废"));
        try {
            refresh.refreshOneForPortfolioFund(portfolioFundId);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("组合基金 {} 行情刷新失败", portfolioFundId, ex);
            throw new BusinessException(ErrorCode.MARKET_DATA_ALL_SOURCES_FAILED, "行情刷新失败，请稍后重试");
        }
        return new Result(portfolioFundId);
    }

    public record Result(long portfolioFundId) {}
}
