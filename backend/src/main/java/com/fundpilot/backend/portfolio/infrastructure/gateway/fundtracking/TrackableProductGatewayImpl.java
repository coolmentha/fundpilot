package com.fundpilot.backend.portfolio.infrastructure.gateway.fundtracking;

import com.fundpilot.backend.portfolio.application.gateway.fundtracking.TrackableProductGateway;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TrackableProductGatewayImpl implements TrackableProductGateway {
    private final FundProductApi productCatalogApi;

    @Override
    public boolean exists(long fundProductId) {
        return productCatalogApi.findById(fundProductId).isPresent();
    }
}
