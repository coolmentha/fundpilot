package com.fundpilot.backend.discipline.application.gateway.classification;

public interface ClassificationPortfolioFundGateway {
    void requireTracked(long ownerId, long portfolioFundId);
}
