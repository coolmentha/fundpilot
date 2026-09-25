package com.fundpilot.backend.marketdata.infrastructure.gateway.indicatorcompute;

import com.fundpilot.backend.marketdata.application.gateway.indicatorcompute.IndicatorFundGateway;
import com.fundpilot.backend.marketdata.application.gateway.navpublishing.TrackedNavProductGateway.InvestmentTarget;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 组合 productcatalog 的产品目录，按产品标识提供基金静态信息。 */
@Component
@RequiredArgsConstructor
class IndicatorFundGatewayImpl implements IndicatorFundGateway {

    private final FundProductApi products;

    @Override
    public Optional<IndicatorFund> findById(long fundProductId) {
        if (fundProductId <= 0) {
            return Optional.empty();
        }
        return products.findById(fundProductId).map(product -> new IndicatorFund(product.id(),
                product.fundCode(), product.fundName(), product.benchmarkIndexCode(),
                product.investmentTarget() == null ? null
                        : InvestmentTarget.valueOf(product.investmentTarget().name())));
    }
}