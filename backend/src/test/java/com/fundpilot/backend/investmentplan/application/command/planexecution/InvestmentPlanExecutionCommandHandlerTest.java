package com.fundpilot.backend.investmentplan.application.command.planexecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.investmentplan.application.gateway.planexecution.PlanTradingCalendarGateway;
import com.fundpilot.backend.investmentplan.application.gateway.planexecution.PlanTransactionGateway;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlan;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanFrequency;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanRepository;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InvestmentPlanExecutionCommandHandlerTest {
    private static final Instant MONDAY = Instant.parse("2026-07-27T06:55:00Z");

    @Test
    void 交易日按计划创建带业务日的账目() {
        InvestmentPlanRepository plans = mock(InvestmentPlanRepository.class);
        PlanTradingCalendarGateway calendar = mock(PlanTradingCalendarGateway.class);
        PlanTransactionGateway transactions = mock(PlanTransactionGateway.class);
        when(plans.findById(7L)).thenReturn(Optional.of(plan()));
        when(calendar.isTradingDay(Instant.parse("2026-07-27T00:00:00Z"))).thenReturn(true);
        when(calendar.latestBefore(Instant.parse("2026-07-27T00:00:00Z"))).thenReturn(Optional.empty());

        boolean created = new InvestmentPlanExecutionCommandHandler(plans, calendar, transactions)
                .execute(7L, MONDAY);

        assertThat(created).isTrue();
        verify(transactions).createPending(3L, 11L, new BigDecimal("100.00"),
                Instant.parse("2026-07-27T00:00:00Z"), 7L);
    }

    @Test
    void 已存在同日账目视为幂等跳过() {
        InvestmentPlanRepository plans = mock(InvestmentPlanRepository.class);
        PlanTradingCalendarGateway calendar = mock(PlanTradingCalendarGateway.class);
        PlanTransactionGateway transactions = mock(PlanTransactionGateway.class);
        when(plans.findById(7L)).thenReturn(Optional.of(plan()));
        when(calendar.isTradingDay(Instant.parse("2026-07-27T00:00:00Z"))).thenReturn(true);
        when(calendar.latestBefore(Instant.parse("2026-07-27T00:00:00Z"))).thenReturn(Optional.empty());
        doThrow(new PlanTransactionGateway.AlreadyExecuted("duplicate"))
                .when(transactions).createPending(3L, 11L, new BigDecimal("100.00"),
                        Instant.parse("2026-07-27T00:00:00Z"), 7L);

        assertThat(new InvestmentPlanExecutionCommandHandler(plans, calendar, transactions).execute(7L, MONDAY))
                .isFalse();
    }

    private static InvestmentPlan plan() {
        return InvestmentPlan.rehydrate(7L, 17L, 11L, 3L, true, new BigDecimal("100.00"),
                InvestmentPlanFrequency.WEEKLY, 1, null, InvestmentPlanStatus.EFFECTIVE);
    }
}
