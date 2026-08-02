package com.fundpilot.backend.investmentplan.application.query.budgetmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.investmentplan.application.gateway.planexecution.PlanTransactionGateway;
import com.fundpilot.backend.investmentplan.application.query.planexecution.InvestmentPlanForecastQueryHandler;
import com.fundpilot.backend.investmentplan.application.query.planexecution.InvestmentPlanVisibleQueryHandler;
import com.fundpilot.backend.investmentplan.domain.budget.InvestmentPlanBudgetRepository;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlan;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanFrequency;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanRepository;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InvestmentPlanBudgetSummaryQueryHandlerTest {
    private static final Instant NOW = Instant.parse("2026-07-27T06:00:00Z");

    @Test
    void 已投入金额采用账目模块的全量投资汇总() {
        InvestmentPlanBudgetRepository budgets = mock(InvestmentPlanBudgetRepository.class);
        PlanTransactionGateway transactions = mock(PlanTransactionGateway.class);
        InvestmentPlanForecastQueryHandler forecasts = mock(InvestmentPlanForecastQueryHandler.class);
        InvestmentPlanVisibleQueryHandler visiblePlans = mock(InvestmentPlanVisibleQueryHandler.class);
        InvestmentPlan plan = InvestmentPlan.rehydrate(7L, 17L, 11L, 3L, true, new BigDecimal("100"),
                InvestmentPlanFrequency.DAILY, null, null, InvestmentPlanStatus.EFFECTIVE);
        Instant monthStart = Instant.parse("2026-07-01T00:00:00Z");
        Instant monthEnd = Instant.parse("2026-08-01T00:00:00Z");
        when(transactions.investedAmount(3L, monthStart, monthEnd)).thenReturn(new BigDecimal("150"));
        when(visiblePlans.findByOwner(3L)).thenReturn(List.of(plan));
        when(forecasts.currentMonthExecutionDates(3L, List.of(plan)))
                .thenReturn(Map.of(7L, List.of(Instant.parse("2026-07-28T00:00:00Z"))));

        var handler = new InvestmentPlanBudgetSummaryQueryHandler(budgets, transactions, forecasts, visiblePlans,
                Clock.fixed(NOW, ZoneOffset.UTC));

        var summary = handler.currentMonth(3L);

        assertThat(summary.investedAmount()).isEqualByComparingTo("150");
        assertThat(summary.futureAmount()).isEqualByComparingTo("100");
        assertThat(summary.projectedAmount()).isEqualByComparingTo("250");
    }
}
