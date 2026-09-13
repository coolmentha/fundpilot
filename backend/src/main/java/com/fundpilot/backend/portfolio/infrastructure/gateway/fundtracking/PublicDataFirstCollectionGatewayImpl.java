package com.fundpilot.backend.portfolio.infrastructure.gateway.fundtracking;

import com.fundpilot.backend.portfolio.application.gateway.fundtracking.PublicDataFirstCollectionGateway;
import com.fundpilot.backend.productcatalog.adapter.api.publicdata.FundPublicDataApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PublicDataFirstCollectionGatewayImpl implements PublicDataFirstCollectionGateway {
    private final FundPublicDataApi publicDataApi;

    @Override
    public void requestFirstCollection(long fundProductId) {
        publicDataApi.requestFirstCollection(fundProductId);
    }
}
