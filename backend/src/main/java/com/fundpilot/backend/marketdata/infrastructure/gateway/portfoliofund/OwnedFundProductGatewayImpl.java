package com.fundpilot.backend.marketdata.infrastructure.gateway.portfoliofund;

import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi;
import com.fundpilot.backend.marketdata.application.gateway.portfoliofund.OwnedFundProductGateway;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
class OwnedFundProductGatewayImpl implements OwnedFundProductGateway {
    private final CurrentActorApi actor;
    private final PortfolioFundApi portfolioFunds;
    private final FundProductApi products;

    @Override
    public Optional<Product> findOwned(long legacyFundId) {
        return trackedProduct(portfolioFunds.findOwnedByLegacyFundId(actor.userId(), legacyFundId));
    }

    @Override
    public Optional<Product> findOwnedByPortfolioFundId(long portfolioFundId) {
        return trackedProduct(portfolioFunds.findOwned(actor.userId(), portfolioFundId));
    }

    private Optional<Product> trackedProduct(Optional<PortfolioFundApi.PortfolioFund> portfolio) {
        return portfolio.filter(value -> value.validity() == PortfolioFundApi.Validity.TRACKED)
                .flatMap(this::product);
    }

    private Optional<Product> product(PortfolioFundApi.PortfolioFund portfolio) {
        return products.findById(portfolio.fundProductId()).map(product -> new Product(product.id(), product.fundCode(),
                product.benchmarkIndexCode(), product.productType() == null ? ProductType.ACTIVE
                : ProductType.valueOf(product.productType().name())));
    }
}
