package com.fundpilot.backend.discipline.infrastructure.gateway.advicegeneration;

import com.fundpilot.backend.discipline.application.gateway.advicegeneration.GeneratedAdvicePortfolioGateway;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.platform.web.error.BusinessException;
import com.fundpilot.backend.platform.web.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GeneratedAdvicePortfolioGatewayImpl implements GeneratedAdvicePortfolioGateway {
    private final PortfolioFundApi portfolioFunds;

    @Override
    public PortfolioFund requireTracked(long ownerId, long legacyFundId) {
        var fund = portfolioFunds.findOwnedByLegacyFundId(ownerId, legacyFundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "组合基金不存在"));
        return tracked(fund);
    }

    @Override
    public PortfolioFund requireTrackedByPortfolioFundId(long ownerId, long portfolioFundId) {
        return tracked(portfolioFunds.findOwned(ownerId, portfolioFundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "组合基金不存在")));
    }

    private static PortfolioFund tracked(PortfolioFundApi.PortfolioFund fund) {
        if (fund.validity() != PortfolioFundApi.Validity.TRACKED) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION, "作废组合基金不生成建议");
        }
        return new PortfolioFund(fund.id(), fund.legacyFundId());
    }
    @Override public java.util.List<PortfolioFund> findTrackedByOwner(long ownerId) {
        return portfolioFunds.findByOwner(ownerId).stream().filter(f -> f.validity() == PortfolioFundApi.Validity.TRACKED)
                .map(f -> new PortfolioFund(f.id(), f.legacyFundId())).toList();
    }

}
