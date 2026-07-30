package com.fundpilot.backend.investmentplan.domain.budget;

import java.util.Optional;

public interface InvestmentPlanBudgetRepository {
    Optional<InvestmentPlanBudget> findByOwnerId(long ownerId);
    InvestmentPlanBudget save(InvestmentPlanBudget budget);
}
