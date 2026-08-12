package com.fundpilot.backend.accounting.infrastructure.gateway.portfoliocorrection;

import com.fundpilot.backend.accounting.application.gateway.portfoliocorrection.CorrectablePortfolioFundGateway;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class CorrectablePortfolioFundGatewayImpl implements CorrectablePortfolioFundGateway {
    private final PortfolioFundApi portfolioFundApi;

    @Override
    public Optional<PortfolioFund> findOwned(long ownerId, long portfolioFundId) {
        return portfolioFundApi.findOwned(ownerId, portfolioFundId).map(result ->
                new PortfolioFund(result.id(), result.legacyFundId(),
                        Validity.valueOf(result.validity().name()), result.voidedAt(),
                        result.voidedBy(), result.voidReason()));
    }

    @Override
    public Optional<PortfolioFund> findOwnedForUpdate(long ownerId, long portfolioFundId) {
        return portfolioFundApi.findForUpdate(portfolioFundId)
                .filter(result -> result.ownerId() == ownerId)
                .map(result -> new PortfolioFund(result.id(), result.legacyFundId(),
                        Validity.valueOf(result.validity().name()), result.voidedAt(),
                        result.voidedBy(), result.voidReason()));
    }

    @Override
    public VoidResult voidPortfolioFund(long ownerId, long portfolioFundId, long actorId,
                                        String reason, Instant occurredAt) {
        try {
            var result = portfolioFundApi.voidPortfolioFund(new PortfolioFundApi.VoidPortfolioFund(
                    ownerId, portfolioFundId, actorId, reason, occurredAt));
            return new VoidResult(result.id(), result.changed(), result.voidedAt(),
                    result.voidedBy(), result.voidReason());
        } catch (PortfolioFundApi.Failure failure) {
            throw new CorrectablePortfolioFundGateway.Rejected(
                    mapReason(failure.code()), failure.getMessage(), failure);
        }
    }

    private CorrectablePortfolioFundGateway.Reason mapReason(PortfolioFundApi.FailureCode code) {
        return switch (code) {
            case PORTFOLIO_FUND_NOT_FOUND -> CorrectablePortfolioFundGateway.Reason.NOT_FOUND;
            case VOID_REASON_REQUIRED -> CorrectablePortfolioFundGateway.Reason.INVALID_REASON;
            case PRODUCT_NOT_FOUND, PORTFOLIO_FUND_ALREADY_TRACKED, POSITION_WARNING_INVALID,
                    PORTFOLIO_FUND_VOIDED -> CorrectablePortfolioFundGateway.Reason.CONFLICT;
        };
    }
}
