package com.fundpilot.backend.investmentplan.infrastructure.persistence.budget;

import com.fundpilot.backend.investmentplan.domain.budget.InvestmentPlanBudget;
import com.fundpilot.backend.investmentplan.domain.budget.InvestmentPlanBudgetRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class InvestmentPlanBudgetRepositoryImpl implements InvestmentPlanBudgetRepository {
    private final InvestmentPlanBudgetJpaRepository budgets;
    @Override public Optional<InvestmentPlanBudget> findByOwnerId(long ownerId) {
        return budgets.findByOwnerId(ownerId).map(this::toDomain);
    }
    @Override public InvestmentPlanBudget save(InvestmentPlanBudget budget) {
        InvestmentPlanBudgetJpaEntity entity = budget.id() == null ? new InvestmentPlanBudgetJpaEntity()
                : budgets.findById(budget.id()).orElseThrow();
        entity.setOwnerId(budget.ownerId());
        entity.setMonthlyBudget(budget.monthlyBudget());
        return toDomain(budgets.save(entity));
    }
    private InvestmentPlanBudget toDomain(InvestmentPlanBudgetJpaEntity entity) {
        return InvestmentPlanBudget.rehydrate(entity.getId(), entity.getOwnerId(), entity.getMonthlyBudget());
    }
}
