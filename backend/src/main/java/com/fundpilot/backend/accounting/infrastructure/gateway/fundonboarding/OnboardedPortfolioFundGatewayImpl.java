package com.fundpilot.backend.accounting.infrastructure.gateway.fundonboarding;

import com.fundpilot.backend.accounting.application.gateway.fundonboarding.OnboardedPortfolioFundGateway;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 将 Portfolio 的公开开户结果转换为 Accounting 的组合基金事实。 */
@Component
@RequiredArgsConstructor
public class OnboardedPortfolioFundGatewayImpl implements OnboardedPortfolioFundGateway {
    private final PortfolioFundApi portfolioFunds;

    @Override
    public OnboardedPortfolioFund track(Long legacyFundId, long ownerId, long fundProductId,
                                        boolean positionWarningEnabled, BigDecimal positionWarningRatio) {
        try {
            var portfolioFund = portfolioFunds.track(new PortfolioFundApi.TrackPortfolioFund(
                    legacyFundId, ownerId, fundProductId, positionWarningEnabled, positionWarningRatio));
            return new OnboardedPortfolioFund(portfolioFund.id(), portfolioFund.ownerId(),
                    portfolioFund.fundProductId());
        } catch (PortfolioFundApi.Failure failure) {
            throw new Rejected(switch (failure.code()) {
                case PRODUCT_NOT_FOUND -> Rejected.Reason.PRODUCT_NOT_FOUND;
                case PORTFOLIO_FUND_ALREADY_TRACKED -> Rejected.Reason.ALREADY_TRACKED;
                default -> Rejected.Reason.INVALID_POSITION_WARNING;
            }, failure.getMessage());
        }
    }
}
