package com.fundpilot.backend.investmentplan.application.command.budgetmanagement;

import com.fundpilot.backend.investmentplan.domain.budget.InvestmentPlanBudget;
import com.fundpilot.backend.investmentplan.domain.budget.InvestmentPlanBudgetRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvestmentPlanBudgetCommandHandler {
    private final InvestmentPlanBudgetRepository budgets;
    @Transactional
    public BigDecimal set(long ownerId, BigDecimal monthlyBudget) {
        InvestmentPlanBudget budget = budgets.findByOwnerId(ownerId)
                .orElseGet(() -> InvestmentPlanBudget.create(ownerId, monthlyBudget));
        budget.setMonthlyBudget(monthlyBudget);
        return budgets.save(budget).monthlyBudget();
    }
}
