package com.fundpilot.backend.marketdata.infrastructure.gateway.navpublishing;

import com.fundpilot.backend.marketdata.application.gateway.navpublishing.TrackedNavProductGateway;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class TrackedNavProductGatewayImpl implements TrackedNavProductGateway {
    private final PortfolioFundApi portfolioFunds;
    private final FundProductApi products;

    @Override
    public List<TrackedProduct> findAll() {
        var tracked = portfolioFunds.findAllTracked();
        Set<Long> productIds = tracked.stream().map(PortfolioFundApi.PortfolioFund::fundProductId)
                .collect(Collectors.toSet());
        var byId = products.findByIds(productIds).stream()
                .collect(Collectors.toMap(FundProductApi.Product::id, Function.identity()));
        var firstTrackedByProduct = new LinkedHashMap<Long, PortfolioFundApi.PortfolioFund>();
        tracked.forEach(fund -> firstTrackedByProduct.putIfAbsent(fund.fundProductId(), fund));
        return firstTrackedByProduct.values().stream().map(fund -> {
            FundProductApi.Product product = byId.get(fund.fundProductId());
            if (product == null) throw new IllegalStateException("跟踪基金缺少产品: " + fund.fundProductId());
            return toTrackedProduct(fund, product);
        }).toList();
    }

    @Override
    public Optional<TrackedProduct> findByLegacyFundId(long legacyFundId) {
        return portfolioFunds.findByLegacyFundId(legacyFundId).filter(fund ->
                fund.validity() == PortfolioFundApi.Validity.TRACKED).flatMap(fund ->
                products.findById(fund.fundProductId()).map(product -> toTrackedProduct(fund, product)));
    }

    @Override
    public Optional<TrackedProduct> findByPortfolioFundId(long portfolioFundId) {
        return portfolioFunds.findById(portfolioFundId).filter(fund ->
                fund.validity() == PortfolioFundApi.Validity.TRACKED).flatMap(fund ->
                products.findById(fund.fundProductId()).map(product -> toTrackedProduct(fund, product)));
    }

    private static TrackedProduct toTrackedProduct(PortfolioFundApi.PortfolioFund fund,
                                                    FundProductApi.Product product) {
        return new TrackedProduct(fund.legacyFundId(), product.id(), product.fundCode(),
                product.fundName(), product.benchmarkIndexCode(), product.investmentTarget() == null ? null
                : InvestmentTarget.valueOf(product.investmentTarget().name()));
    }
}
