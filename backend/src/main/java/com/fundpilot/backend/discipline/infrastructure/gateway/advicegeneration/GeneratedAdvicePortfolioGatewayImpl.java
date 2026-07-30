package com.fundpilot.backend.discipline.infrastructure.gateway.advicegeneration;

import com.fundpilot.backend.discipline.application.gateway.advicegeneration.GeneratedAdvicePortfolioGateway;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GeneratedAdvicePortfolioGatewayImpl implements GeneratedAdvicePortfolioGateway {
    private final PortfolioFundApi portfolioFunds;

    @Override
    public PortfolioFund requireTracked(long ownerId, long legacyFundId) {
        var fund = portfolioFunds.findOwnedByLegacyFundId(ownerId, legacyFundId)
                .orElseThrow(() -> new Rejected("组合基金不存在"));
        return tracked(fund);
    }

    @Override
    public PortfolioFund requireTrackedByPortfolioFundId(long ownerId, long portfolioFundId) {
        return tracked(portfolioFunds.findOwned(ownerId, portfolioFundId)
                .orElseThrow(() -> new Rejected("组合基金不存在")));
    }

    private static PortfolioFund tracked(PortfolioFundApi.PortfolioFund fund) {
        if (fund.validity() != PortfolioFundApi.Validity.TRACKED) {
            throw new Rejected("作废组合基金不生成建议");
        }
        return new PortfolioFund(fund.id(), fund.legacyFundId());
    }
    @Override public java.util.List<PortfolioFund> findTrackedByOwner(long ownerId) {
        return portfolioFunds.findByOwner(ownerId).stream().filter(f -> f.validity() == PortfolioFundApi.Validity.TRACKED)
                .map(f -> new PortfolioFund(f.id(), f.legacyFundId())).toList();
    }

    public static final class Rejected extends RuntimeException { public Rejected(String message) { super(message); } }
}
