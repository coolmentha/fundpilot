package com.fundpilot.backend.investmentplan.application.query.planexecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.investmentplan.application.command.planmanagement.InvestmentPlanCommandHandler;
import com.fundpilot.backend.investmentplan.application.gateway.planmanagement.PlanPortfolioFundGateway;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlan;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanFrequency;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanRepository;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InvestmentPlanQueryHandlerTest {
    @Test
    void 全局列表使用共享可见计划集合() {
        InvestmentPlanRepository plans = mock(InvestmentPlanRepository.class);
        PlanPortfolioFundGateway portfolioFunds = mock(PlanPortfolioFundGateway.class);
        InvestmentPlanForecastQueryHandler forecasts = mock(InvestmentPlanForecastQueryHandler.class);
        InvestmentPlanVisibleQueryHandler visiblePlans = mock(InvestmentPlanVisibleQueryHandler.class);
        InvestmentPlan plan = InvestmentPlan.rehydrate(7L, 17L, 11L, 3L, true, new BigDecimal("100"),
                InvestmentPlanFrequency.DAILY, null, null, InvestmentPlanStatus.EFFECTIVE);
        when(visiblePlans.findByOwner(3L)).thenReturn(List.of(plan));
        when(forecasts.currentMonthExecutionDates(3L, List.of(plan))).thenReturn(Map.of());

        var result = new InvestmentPlanQueryHandler(plans, portfolioFunds, forecasts, visiblePlans).list(3L);

        assertThat(result).extracting(InvestmentPlanCommandHandler.PlanResult::id).containsExactly(7L);
        verify(visiblePlans).findByOwner(3L);
        verify(plans, never()).findByOwnerId(3L);
    }
}
