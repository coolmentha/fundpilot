package com.fundpilot.backend.discipline.infrastructure.gateway.strategymanagement;
import com.fundpilot.backend.discipline.application.gateway.strategymanagement.StrategyPortfolioFundGateway;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor public class StrategyPortfolioFundGatewayImpl implements StrategyPortfolioFundGateway {
    private final PortfolioFundApi funds;
    @Override public PortfolioFund requireTrackedByLegacyFund(long ownerId, long legacyFundId) { return checked(funds.findOwnedByLegacyFundId(ownerId, legacyFundId).orElseThrow(() -> new Rejected("组合基金不存在"))); }
    @Override public PortfolioFund requireTracked(long ownerId, long portfolioFundId) { return checked(funds.findOwned(ownerId, portfolioFundId).orElseThrow(() -> new Rejected("组合基金不存在"))); }
    private PortfolioFund checked(PortfolioFundApi.PortfolioFund fund) { if (fund.validity() != PortfolioFundApi.Validity.TRACKED) throw new Rejected("作废组合基金不能管理策略"); return new PortfolioFund(fund.id()); }
    public static final class Rejected extends RuntimeException { public Rejected(String message) { super(message); } }
}
