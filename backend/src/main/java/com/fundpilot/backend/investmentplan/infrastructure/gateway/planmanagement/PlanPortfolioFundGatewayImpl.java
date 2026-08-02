package com.fundpilot.backend.investmentplan.infrastructure.gateway.planmanagement;

import com.fundpilot.backend.investmentplan.application.gateway.planmanagement.PlanPortfolioFundGateway;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.platform.web.error.BusinessException;
import com.fundpilot.backend.platform.web.error.ErrorCode;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlanPortfolioFundGatewayImpl implements PlanPortfolioFundGateway {
    private final PortfolioFundApi portfolioFunds;

    @Override public PortfolioFund requireTrackedByLegacyFund(long ownerId, long legacyFundId) {
        var fund = portfolioFunds.findOwnedByLegacyFundId(ownerId, legacyFundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "组合基金不存在"));
        return requireTracked(fund);
    }

    @Override public PortfolioFund requireTracked(long ownerId, long portfolioFundId) {
        var fund = portfolioFunds.findOwned(ownerId, portfolioFundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "组合基金不存在"));
        return requireTracked(fund);
    }

    @Override public List<PortfolioFund> findTrackedByOwner(long ownerId) {
        return portfolioFunds.findByOwner(ownerId).stream()
                .filter(fund -> fund.validity() == PortfolioFundApi.Validity.TRACKED)
                .map(fund -> new PortfolioFund(fund.id(), fund.legacyFundId()))
                .toList();
    }

    @Override public Optional<PortfolioFund> findTrackedForExecution(long ownerId, long portfolioFundId) {
        return portfolioFunds.findForUpdate(portfolioFundId)
                .filter(fund -> fund.ownerId() == ownerId)
                .filter(fund -> fund.validity() == PortfolioFundApi.Validity.TRACKED)
                .map(fund -> new PortfolioFund(fund.id(), fund.legacyFundId()));
    }

    private PortfolioFund requireTracked(PortfolioFundApi.PortfolioFund fund) {
        if (fund.validity() != PortfolioFundApi.Validity.TRACKED) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION, "作废组合基金不能管理定投计划");
        }
        return new PortfolioFund(fund.id(), fund.legacyFundId());
    }
}
