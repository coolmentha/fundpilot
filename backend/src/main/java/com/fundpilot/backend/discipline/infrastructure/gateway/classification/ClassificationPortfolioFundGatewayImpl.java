package com.fundpilot.backend.discipline.infrastructure.gateway.classification;

import com.fundpilot.backend.discipline.application.gateway.classification.ClassificationPortfolioFundGateway;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ClassificationPortfolioFundGatewayImpl implements ClassificationPortfolioFundGateway {
    private final PortfolioFundApi portfolioFunds;

    @Override
    public void requireTracked(long ownerId, long portfolioFundId) {
        PortfolioFundApi.PortfolioFund fund = portfolioFunds.findOwned(ownerId, portfolioFundId)
                .orElseThrow(() -> new IllegalArgumentException("组合基金不存在: " + portfolioFundId));
        if (fund.validity() != PortfolioFundApi.Validity.TRACKED) {
            throw new IllegalArgumentException("作废组合基金不能设置纪律分类: " + portfolioFundId);
        }
    }
}
