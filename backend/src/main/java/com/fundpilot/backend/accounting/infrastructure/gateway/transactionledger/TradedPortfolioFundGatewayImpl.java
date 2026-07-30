package com.fundpilot.backend.accounting.infrastructure.gateway.transactionledger;

import com.fundpilot.backend.accounting.application.gateway.transactionledger.TradedPortfolioFundGateway;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Converts Portfolio's public contract into Accounting's tradable-fund facts. */
@Component
@RequiredArgsConstructor
public class TradedPortfolioFundGatewayImpl implements TradedPortfolioFundGateway {
    private final PortfolioFundApi portfolioFunds;

    @Override
    public Optional<TradedPortfolioFund> find(long portfolioFundId) {
        return portfolioFunds.findById(portfolioFundId).map(TradedPortfolioFundGatewayImpl::from);
    }

    @Override
    public Optional<TradedPortfolioFund> findOwned(long ownerId, long portfolioFundId) {
        return portfolioFunds.findOwned(ownerId, portfolioFundId).map(TradedPortfolioFundGatewayImpl::from);
    }

    @Override
    public Optional<TradedPortfolioFund> findByLegacyFundId(long legacyFundId) {
        return portfolioFunds.findByLegacyFundId(legacyFundId).map(TradedPortfolioFundGatewayImpl::from);
    }

    @Override
    public List<TradedPortfolioFund> findTradableByOwner(long ownerId) {
        return portfolioFunds.findByOwner(ownerId).stream()
                .map(TradedPortfolioFundGatewayImpl::from)
                .filter(TradedPortfolioFund::tradable)
                .toList();
    }

    private static TradedPortfolioFund from(PortfolioFundApi.PortfolioFund value) {
        return new TradedPortfolioFund(value.id(), value.ownerId(), value.fundProductId(),
                value.legacyFundId(), value.validity() == PortfolioFundApi.Validity.TRACKED);
    }
}
