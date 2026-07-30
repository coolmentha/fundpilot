package com.fundpilot.backend.investmentplan.domain.investmentplan;

import java.util.List;
import java.util.Optional;

public interface InvestmentPlanRepository {
    Optional<InvestmentPlan> findById(long id);
    List<InvestmentPlan> findByPortfolioFundId(long portfolioFundId);
    Optional<InvestmentPlan> findEffectiveByPortfolioFundId(long portfolioFundId);
    List<InvestmentPlan> findByOwnerId(long ownerId);
    List<InvestmentPlan> findEffectiveEnabled();
    InvestmentPlan save(InvestmentPlan plan);
    void delete(InvestmentPlan plan);
}
