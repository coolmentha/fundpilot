package com.fundpilot.backend.portfolio.infrastructure.gateway.fundtracking;

import com.fundpilot.backend.portfolio.application.gateway.fundtracking.FundProductGateway;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class FundProductGatewayImpl implements FundProductGateway {
    private final FundProductApi products;

    @Override
    public List<Product> findByIds(Set<Long> ids) {
        return products.findByIds(ids).stream()
                .map(product -> new Product(product.id(), product.fundCode(), product.fundName(),
                        product.productType() == null ? null : product.productType().name(),
                        product.investmentTarget() == null ? null : product.investmentTarget().name(),
                        product.benchmarkIndexCode()))
                .toList();
    }
}
