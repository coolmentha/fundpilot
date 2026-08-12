package com.fundpilot.backend.investmentplan.application.query.planexecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.investmentplan.application.gateway.planexecution.PlanTradingCalendarGateway;
import com.fundpilot.backend.investmentplan.application.gateway.planexecution.PlanTransactionGateway;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlan;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanFrequency;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanStatus;
import com.fundpilot.backend.investmentplan.domain.execution.InvestmentPlanExecutionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InvestmentPlanForecastQueryHandlerTest {
    private static final Instant NOW = Instant.parse("2026-07-27T06:00:00Z");

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "CONFIRMED", "CANCELLED"})
    void 已生成的计划日不再计入本月剩余预测(String status) {
        PlanTradingCalendarGateway calendar = mock(PlanTradingCalendarGateway.class);
        PlanTransactionGateway transactions = mock(PlanTransactionGateway.class);
        Instant july27 = Instant.parse("2026-07-27T00:00:00Z");
        Instant july28 = Instant.parse("2026-07-28T00:00:00Z");
        when(calendar.tradingDaysBetween(Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"))).thenReturn(List.of(july27, july28));
        when(transactions.occurrences(3L, Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"))).thenReturn(List.of(
                new PlanTransactionGateway.Occurrence(7L, july27, new BigDecimal("100"), status)));

        var handler = new InvestmentPlanForecastQueryHandler(calendar, transactions,
                Clock.fixed(NOW, ZoneOffset.UTC), mock(InvestmentPlanExecutionRepository.class));

        assertThat(handler.currentMonthExecutionDates(3L, List.of(dailyPlan())))
                .containsEntry(7L, List.of(july28));
    }

    @Test
    void 月定投月初首日不使用null前序交易日避免幻影预测() {
        PlanTradingCalendarGateway calendar = mock(PlanTradingCalendarGateway.class);
        PlanTransactionGateway transactions = mock(PlanTransactionGateway.class);
        Instant monthStart = Instant.parse("2026-08-01T00:00:00Z");
        Instant nextMonthStart = Instant.parse("2026-09-01T00:00:00Z");
        Instant aug3 = Instant.parse("2026-08-03T00:00:00Z");
        Instant aug10 = Instant.parse("2026-08-10T00:00:00Z");
        when(calendar.latestBefore(monthStart)).thenReturn(Optional.of(Instant.parse("2026-07-31T00:00:00Z")));
        when(calendar.tradingDaysBetween(monthStart, nextMonthStart)).thenReturn(List.of(aug3, aug10));
        when(transactions.occurrences(3L, monthStart, nextMonthStart)).thenReturn(List.of());

        var handler = new InvestmentPlanForecastQueryHandler(calendar, transactions,
                Clock.fixed(Instant.parse("2026-08-03T05:00:00Z"), ZoneOffset.UTC),
                mock(InvestmentPlanExecutionRepository.class));

        assertThat(handler.currentMonthExecutionDates(3L, List.of(monthlyPlan())))
                .containsEntry(7L, List.of(aug10));
    }

    private static InvestmentPlan dailyPlan() {
        return InvestmentPlan.rehydrate(7L, 17L, 11L, 3L, true, new BigDecimal("100"),
                InvestmentPlanFrequency.DAILY, null, null, InvestmentPlanStatus.EFFECTIVE);
    }

    private static InvestmentPlan monthlyPlan() {
        return InvestmentPlan.rehydrate(7L, 17L, 11L, 3L, true, new BigDecimal("100"),
                InvestmentPlanFrequency.MONTHLY, null, 10, InvestmentPlanStatus.EFFECTIVE);
    }
}
