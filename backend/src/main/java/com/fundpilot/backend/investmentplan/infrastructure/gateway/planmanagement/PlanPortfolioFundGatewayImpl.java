package com.fundpilot.backend.investmentplan.infrastructure.gateway.planmanagement;

import com.fundpilot.backend.investmentplan.application.gateway.planmanagement.PlanPortfolioFundGateway;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlanPortfolioFundGatewayImpl implements PlanPortfolioFundGateway {
    private final PortfolioFundApi portfolioFunds;

    @Override public PortfolioFund requireTrackedByLegacyFund(long ownerId, long legacyFundId) {
        var fund = portfolioFunds.findOwnedByLegacyFundId(ownerId, legacyFundId)
                .orElseThrow(() -> new Rejected("组合基金不存在"));
        return requireTracked(fund);
    }

    @Override public PortfolioFund requireTracked(long ownerId, long portfolioFundId) {
        var fund = portfolioFunds.findOwned(ownerId, portfolioFundId)
                .orElseThrow(() -> new Rejected("组合基金不存在"));
        return requireTracked(fund);
    }

    private PortfolioFund requireTracked(PortfolioFundApi.PortfolioFund fund) {
        if (fund.validity() != PortfolioFundApi.Validity.TRACKED) throw new Rejected("作废组合基金不能管理定投计划");
        return new PortfolioFund(fund.id(), fund.legacyFundId());
    }

    public static final class Rejected extends RuntimeException { public Rejected(String message) { super(message); } }
}
