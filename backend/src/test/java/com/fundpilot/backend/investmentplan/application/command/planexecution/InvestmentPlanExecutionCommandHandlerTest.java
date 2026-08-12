package com.fundpilot.backend.investmentplan.application.command.planexecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.investmentplan.application.gateway.planexecution.PlanInvestmentFactsGateway;
import com.fundpilot.backend.investmentplan.application.gateway.planexecution.PlanTradingCalendarGateway;
import com.fundpilot.backend.investmentplan.application.gateway.planexecution.PlanTransactionGateway;
import com.fundpilot.backend.investmentplan.application.gateway.planmanagement.PlanPortfolioFundGateway;
import com.fundpilot.backend.investmentplan.domain.execution.InvestmentPlanExecution;
import com.fundpilot.backend.investmentplan.domain.execution.InvestmentPlanExecutionRepository;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanAmountStrategy;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlan;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanFrequency;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanRepository;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanStatus;
import com.fundpilot.backend.investmentplan.domain.investmentplan.SmartInvestmentAmountPolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InvestmentPlanExecutionCommandHandlerTest {
    private static final Instant MONDAY = Instant.parse("2026-07-27T06:55:00Z");

    @Test
    void 交易日按计划创建带业务日的账目() {
        InvestmentPlanRepository plans = mock(InvestmentPlanRepository.class);
        PlanTradingCalendarGateway calendar = mock(PlanTradingCalendarGateway.class);
        PlanTransactionGateway transactions = mock(PlanTransactionGateway.class);
        PlanPortfolioFundGateway portfolioFunds = mock(PlanPortfolioFundGateway.class);
        when(plans.findById(7L)).thenReturn(Optional.of(plan()));
        when(portfolioFunds.findTrackedForExecution(3L, 11L))
                .thenReturn(Optional.of(new PlanPortfolioFundGateway.PortfolioFund(11L, 101L)));
        when(calendar.isTradingDay(Instant.parse("2026-07-27T00:00:00Z"))).thenReturn(true);
        when(transactions.occurrences(3L, Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"))).thenReturn(List.of());

        boolean created = handler(plans, calendar, transactions, portfolioFunds)
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
        PlanPortfolioFundGateway portfolioFunds = mock(PlanPortfolioFundGateway.class);
        when(plans.findById(7L)).thenReturn(Optional.of(plan()));
        when(portfolioFunds.findTrackedForExecution(3L, 11L))
                .thenReturn(Optional.of(new PlanPortfolioFundGateway.PortfolioFund(11L, 101L)));
        when(calendar.isTradingDay(Instant.parse("2026-07-27T00:00:00Z"))).thenReturn(true);
        when(transactions.occurrences(3L, Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"))).thenReturn(List.of());
        doThrow(new PlanTransactionGateway.AlreadyExecuted("duplicate"))
                .when(transactions).createPending(3L, 11L, new BigDecimal("100.00"),
                        Instant.parse("2026-07-27T00:00:00Z"), 7L);

        assertThat(handler(plans, calendar, transactions, portfolioFunds)
                .execute(7L, MONDAY))
                .isFalse();
    }

    @Test
    void 月定投本月已有账目则不再执行() {
        InvestmentPlanRepository plans = mock(InvestmentPlanRepository.class);
        PlanTradingCalendarGateway calendar = mock(PlanTradingCalendarGateway.class);
        PlanTransactionGateway transactions = mock(PlanTransactionGateway.class);
        PlanPortfolioFundGateway portfolioFunds = mock(PlanPortfolioFundGateway.class);
        when(plans.findById(7L)).thenReturn(Optional.of(InvestmentPlan.rehydrate(7L, 17L, 11L, 3L, true,
                new BigDecimal("100.00"), InvestmentPlanFrequency.MONTHLY, null, 2, InvestmentPlanStatus.EFFECTIVE)));
        when(portfolioFunds.findTrackedForExecution(3L, 11L))
                .thenReturn(Optional.of(new PlanPortfolioFundGateway.PortfolioFund(11L, 101L)));
        when(calendar.isTradingDay(Instant.parse("2026-07-27T00:00:00Z"))).thenReturn(true);
        when(transactions.occurrences(3L, Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"))).thenReturn(List.of(
                new PlanTransactionGateway.Occurrence(7L, Instant.parse("2026-07-27T00:00:00Z"),
                        new BigDecimal("100"), "PENDING")));

        assertThat(handler(plans, calendar, transactions, portfolioFunds)
                .execute(7L, MONDAY))
                .isFalse();
        verify(transactions, never()).createPending(anyLong(), anyLong(), any(), any(), anyLong());
    }

    @Test
    void 作废组合基金不会创建新的定投流水() {
        InvestmentPlanRepository plans = mock(InvestmentPlanRepository.class);
        PlanTradingCalendarGateway calendar = mock(PlanTradingCalendarGateway.class);
        PlanTransactionGateway transactions = mock(PlanTransactionGateway.class);
        PlanPortfolioFundGateway portfolioFunds = mock(PlanPortfolioFundGateway.class);
        when(plans.findById(7L)).thenReturn(Optional.of(plan()));
        when(portfolioFunds.findTrackedForExecution(3L, 11L)).thenReturn(Optional.empty());
        when(calendar.isTradingDay(Instant.parse("2026-07-27T00:00:00Z"))).thenReturn(true);

        assertThat(handler(plans, calendar, transactions, portfolioFunds)
                .execute(7L, MONDAY)).isFalse();
        verify(portfolioFunds).findTrackedForExecution(3L, 11L);
        verify(transactions, never()).createPending(anyLong(), anyLong(), any(), any(), anyLong());
    }

    @Test
    void 智能均线按事实计算金额并记录执行决策() {
        InvestmentPlanRepository plans = mock(InvestmentPlanRepository.class);
        PlanTradingCalendarGateway calendar = mock(PlanTradingCalendarGateway.class);
        PlanTransactionGateway transactions = mock(PlanTransactionGateway.class);
        PlanPortfolioFundGateway portfolioFunds = mock(PlanPortfolioFundGateway.class);
        InvestmentPlanExecutionRepository executions = mock(InvestmentPlanExecutionRepository.class);
        PlanInvestmentFactsGateway factsGateway = mock(PlanInvestmentFactsGateway.class);
        InvestmentPlan plan = smartPlan(InvestmentPlanAmountStrategy.MOVING_AVERAGE,
                InvestmentPlanFrequency.WEEKLY, 1, null, 250);
        stubReady(plans, calendar, transactions, portfolioFunds, executions, plan);
        when(factsGateway.load(any(), any(), any())).thenReturn(Optional.of(new PlanInvestmentFactsGateway.Facts(
                new SmartInvestmentAmountPolicy.Facts(null, new BigDecimal("0.96"), new BigDecimal("1"),
                        new BigDecimal("0.04"), null, null), Instant.parse("2026-07-24T00:00:00Z"),
                "000300", 250)));

        boolean created = smartHandler(plans, calendar, transactions, portfolioFunds, executions, factsGateway)
                .execute(7L, MONDAY);

        assertThat(created).isTrue();
        verify(transactions).createPending(3L, 11L, new BigDecimal("160.00"),
                Instant.parse("2026-07-27T00:00:00Z"), 7L);
        ArgumentCaptor<InvestmentPlanExecution> record = ArgumentCaptor.forClass(InvestmentPlanExecution.class);
        verify(executions).insert(record.capture());
        assertThat(record.getValue().result()).isEqualTo(InvestmentPlanExecution.Result.EXECUTED);
        assertThat(record.getValue().actualAmount()).isEqualByComparingTo("160");
        assertThat(record.getValue().deductionRate()).isEqualByComparingTo("1.6");
        assertThat(record.getValue().dataDate()).isEqualTo(Instant.parse("2026-07-24T00:00:00Z"));
    }

    @Test
    void 智能低估不满足时跳过并留痕不创建交易() {
        InvestmentPlanRepository plans = mock(InvestmentPlanRepository.class);
        PlanTradingCalendarGateway calendar = mock(PlanTradingCalendarGateway.class);
        PlanTransactionGateway transactions = mock(PlanTransactionGateway.class);
        PlanPortfolioFundGateway portfolioFunds = mock(PlanPortfolioFundGateway.class);
        InvestmentPlanExecutionRepository executions = mock(InvestmentPlanExecutionRepository.class);
        PlanInvestmentFactsGateway factsGateway = mock(PlanInvestmentFactsGateway.class);
        InvestmentPlan plan = smartPlan(InvestmentPlanAmountStrategy.LOW_VALUATION,
                InvestmentPlanFrequency.WEEKLY, 1, null, null);
        stubReady(plans, calendar, transactions, portfolioFunds, executions, plan);
        when(factsGateway.load(any(), any(), any())).thenReturn(Optional.of(new PlanInvestmentFactsGateway.Facts(
                new SmartInvestmentAmountPolicy.Facts(new BigDecimal("30.01"), null, null, null, null, null),
                Instant.parse("2026-07-24T00:00:00Z"), "000300", null)));

        boolean created = smartHandler(plans, calendar, transactions, portfolioFunds, executions, factsGateway)
                .execute(7L, MONDAY);

        assertThat(created).isFalse();
        verify(transactions, never()).createPending(anyLong(), anyLong(), any(), any(), anyLong());
        ArgumentCaptor<InvestmentPlanExecution> record = ArgumentCaptor.forClass(InvestmentPlanExecution.class);
        verify(executions).insert(record.capture());
        assertThat(record.getValue().result()).isEqualTo(InvestmentPlanExecution.Result.SKIPPED);
        assertThat(record.getValue().reasonCode()).isEqualTo("VALUATION_NOT_LOW");
    }

    @Test
    void 智能计划已有同日决策时不重复读取事实或创建交易() {
        InvestmentPlanRepository plans = mock(InvestmentPlanRepository.class);
        PlanTradingCalendarGateway calendar = mock(PlanTradingCalendarGateway.class);
        PlanTransactionGateway transactions = mock(PlanTransactionGateway.class);
        PlanPortfolioFundGateway portfolioFunds = mock(PlanPortfolioFundGateway.class);
        InvestmentPlanExecutionRepository executions = mock(InvestmentPlanExecutionRepository.class);
        PlanInvestmentFactsGateway factsGateway = mock(PlanInvestmentFactsGateway.class);
        InvestmentPlan plan = smartPlan(InvestmentPlanAmountStrategy.CHANGE_RATE,
                InvestmentPlanFrequency.WEEKLY, 1, null, null);
        stubReady(plans, calendar, transactions, portfolioFunds, executions, plan);
        when(executions.find(7L, Instant.parse("2026-07-27T00:00:00Z"))).thenReturn(Optional.of(
                new InvestmentPlanExecution(1L, 7L, Instant.parse("2026-07-27T00:00:00Z"),
                        InvestmentPlanAmountStrategy.CHANGE_RATE, SmartInvestmentAmountPolicy.RULE_VERSION,
                        InvestmentPlanExecution.Result.SKIPPED, "COST_UNAVAILABLE", "平均持仓成本不可用",
                        new BigDecimal("100"), null, null, Instant.parse("2026-07-24T00:00:00Z"),
                        null, null, null, null)));

        assertThat(smartHandler(plans, calendar, transactions, portfolioFunds, executions, factsGateway)
                .execute(7L, MONDAY)).isFalse();

        verify(factsGateway, never()).load(any(), any(), any());
        verify(transactions, never()).createPending(anyLong(), anyLong(), any(), any(), anyLong());
    }

    @Test
    void 月计划已有跳过决策后本月不补投() {
        InvestmentPlanRepository plans = mock(InvestmentPlanRepository.class);
        PlanTradingCalendarGateway calendar = mock(PlanTradingCalendarGateway.class);
        PlanTransactionGateway transactions = mock(PlanTransactionGateway.class);
        PlanPortfolioFundGateway portfolioFunds = mock(PlanPortfolioFundGateway.class);
        InvestmentPlanExecutionRepository executions = mock(InvestmentPlanExecutionRepository.class);
        PlanInvestmentFactsGateway factsGateway = mock(PlanInvestmentFactsGateway.class);
        InvestmentPlan plan = smartPlan(InvestmentPlanAmountStrategy.LOW_VALUATION,
                InvestmentPlanFrequency.MONTHLY, null, 2, null);
        stubReady(plans, calendar, transactions, portfolioFunds, executions, plan);
        when(executions.existsBetween(7L, Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"))).thenReturn(true);

        assertThat(smartHandler(plans, calendar, transactions, portfolioFunds, executions, factsGateway)
                .execute(7L, MONDAY)).isFalse();

        verify(factsGateway, never()).load(any(), any(), any());
        verify(transactions, never()).createPending(anyLong(), anyLong(), any(), any(), anyLong());
        verify(executions, never()).insert(any());
    }

    private static InvestmentPlan plan() {
        return InvestmentPlan.rehydrate(7L, 17L, 11L, 3L, true, new BigDecimal("100.00"),
                InvestmentPlanFrequency.WEEKLY, 1, null, InvestmentPlanStatus.EFFECTIVE);
    }

    private static InvestmentPlan smartPlan(InvestmentPlanAmountStrategy strategy,
                                            InvestmentPlanFrequency frequency, Integer dayOfWeek,
                                            Integer dayOfMonth, Integer movingAverageDays) {
        return InvestmentPlan.rehydrate(7L, 17L, 11L, 3L, true, new BigDecimal("100.00"), frequency,
                dayOfWeek, dayOfMonth, strategy, "000300", movingAverageDays, InvestmentPlanStatus.EFFECTIVE);
    }

    private static void stubReady(InvestmentPlanRepository plans, PlanTradingCalendarGateway calendar,
                                  PlanTransactionGateway transactions, PlanPortfolioFundGateway portfolioFunds,
                                  InvestmentPlanExecutionRepository executions, InvestmentPlan plan) {
        when(plans.findById(7L)).thenReturn(Optional.of(plan));
        when(portfolioFunds.findTrackedForExecution(3L, 11L)).thenReturn(Optional.of(
                new PlanPortfolioFundGateway.PortfolioFund(11L, 101L, 501L, "000300")));
        when(calendar.isTradingDay(Instant.parse("2026-07-27T00:00:00Z"))).thenReturn(true);
        when(transactions.occurrences(3L, Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"))).thenReturn(List.of());
        when(executions.find(7L, Instant.parse("2026-07-27T00:00:00Z"))).thenReturn(Optional.empty());
    }

    private static InvestmentPlanExecutionCommandHandler smartHandler(
            InvestmentPlanRepository plans, PlanTradingCalendarGateway calendar,
            PlanTransactionGateway transactions, PlanPortfolioFundGateway portfolioFunds,
            InvestmentPlanExecutionRepository executions, PlanInvestmentFactsGateway factsGateway) {
        return new InvestmentPlanExecutionCommandHandler(plans, calendar, transactions, portfolioFunds,
                executions, factsGateway);
    }

    private static InvestmentPlanExecutionCommandHandler handler(InvestmentPlanRepository plans,
                                                                  PlanTradingCalendarGateway calendar,
                                                                  PlanTransactionGateway transactions,
                                                                  PlanPortfolioFundGateway portfolioFunds) {
        return new InvestmentPlanExecutionCommandHandler(plans, calendar, transactions, portfolioFunds,
                mock(InvestmentPlanExecutionRepository.class), mock(PlanInvestmentFactsGateway.class));
    }
}
