package com.fundpilot.backend.investmentplan.application.query.planexecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.investmentplan.application.gateway.planmanagement.PlanPortfolioFundGateway;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlan;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanFrequency;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanRepository;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanStatus;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvestmentPlanVisibleQueryHandlerTest {
    @Test
    void 只返回关联组合基金仍为tracked的计划() {
        InvestmentPlanRepository plans = mock(InvestmentPlanRepository.class);
        PlanPortfolioFundGateway funds = mock(PlanPortfolioFundGateway.class);
        InvestmentPlan trackedPlan = plan(7L, 11L);
        InvestmentPlan voidedPlan = plan(8L, 12L);
        when(plans.findByOwnerId(3L)).thenReturn(List.of(trackedPlan, voidedPlan));
        when(funds.findTrackedByOwner(3L)).thenReturn(List.of(
                new PlanPortfolioFundGateway.PortfolioFund(11L, 41L)));

        var result = new InvestmentPlanVisibleQueryHandler(plans, funds).findByOwner(3L);

        assertThat(result).extracting(InvestmentPlan::id).containsExactly(7L);
    }

    private static InvestmentPlan plan(long id, long portfolioFundId) {
        return InvestmentPlan.rehydrate(id, id + 10L, portfolioFundId, 3L, true,
                new BigDecimal("100"), InvestmentPlanFrequency.DAILY, null, null,
                InvestmentPlanStatus.EFFECTIVE);
    }
}
