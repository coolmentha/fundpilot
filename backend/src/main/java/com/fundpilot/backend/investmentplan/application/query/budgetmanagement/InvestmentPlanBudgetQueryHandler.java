package com.fundpilot.backend.investmentplan.application.query.budgetmanagement;

import com.fundpilot.backend.investmentplan.domain.budget.InvestmentPlanBudgetRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvestmentPlanBudgetQueryHandler {
    private final InvestmentPlanBudgetRepository budgets;
    @Transactional(readOnly = true)
    public BigDecimal get(long ownerId) { return budgets.findByOwnerId(ownerId).map(b -> b.monthlyBudget()).orElse(null); }
}
