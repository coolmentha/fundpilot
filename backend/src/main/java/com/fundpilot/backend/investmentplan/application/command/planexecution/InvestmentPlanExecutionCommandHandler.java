package com.fundpilot.backend.investmentplan.application.command.planexecution;

import com.fundpilot.backend.investmentplan.application.gateway.planexecution.PlanInvestmentFactsGateway;
import com.fundpilot.backend.investmentplan.application.gateway.planexecution.PlanTradingCalendarGateway;
import com.fundpilot.backend.investmentplan.application.gateway.planexecution.PlanTransactionGateway;
import com.fundpilot.backend.investmentplan.application.gateway.planmanagement.PlanPortfolioFundGateway;
import com.fundpilot.backend.investmentplan.application.query.planexecution.InvestmentPlanForecastQueryHandler;
import com.fundpilot.backend.investmentplan.domain.execution.InvestmentPlanExecution;
import com.fundpilot.backend.investmentplan.domain.execution.InvestmentPlanExecutionRepository;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlan;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanAmountStrategy;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanFrequency;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanRepository;
import com.fundpilot.backend.investmentplan.domain.investmentplan.SmartInvestmentAmountPolicy;
import com.fundpilot.backend.sharedkernel.BusinessDay;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 单计划执行事务；定时适配器逐计划调用以隔离失败。 */
@Service
@RequiredArgsConstructor
public class InvestmentPlanExecutionCommandHandler {
    private static final SmartInvestmentAmountPolicy POLICY = new SmartInvestmentAmountPolicy();

    private final InvestmentPlanRepository plans;
    private final PlanTradingCalendarGateway calendar;
    private final PlanTransactionGateway transactions;
    private final PlanPortfolioFundGateway portfolioFunds;
    private final InvestmentPlanExecutionRepository executions;
    private final PlanInvestmentFactsGateway factsGateway;

    @Transactional
    public boolean execute(long planId, Instant now) {
        var plan = plans.findById(planId).orElse(null);
        Instant businessDate = BusinessDay.toDateLabel(now);
        if (plan == null || !calendar.isTradingDay(businessDate)) return false;
        var tracked = portfolioFunds.findTrackedForExecution(plan.ownerId(), plan.portfolioFundId());
        if (tracked.isEmpty()) return false;
        boolean alreadyExecutedThisMonth = hasAnyOccurrenceThisMonth(plan, businessDate);
        Instant latestTradingDayBefore = calendar.latestBefore(businessDate).orElse(null);
        if (!plan.executableOn(businessDate, alreadyExecutedThisMonth, latestTradingDayBefore)) return false;
        if (plan.amountStrategy() == InvestmentPlanAmountStrategy.FIXED) {
            return createFixed(plan, businessDate);
        }
        if (executions.find(plan.id(), businessDate).isPresent()) return false;

        var facts = factsGateway.load(plan, tracked.get(), businessDate).orElseGet(() ->
                new PlanInvestmentFactsGateway.Facts(SmartInvestmentAmountPolicy.Facts.empty(), null,
                        plan.referenceIndexCode(), plan.movingAverageDays()));
        var decision = POLICY.calculate(plan.amountStrategy(), plan.amount(), facts.policyFacts());
        var record = decisionRecord(plan, businessDate, facts, decision);
        if (!decision.executable()) {
            executions.insert(record);
            return false;
        }
        try {
            transactions.createPending(plan.ownerId(), plan.portfolioFundId(), decision.amount(), businessDate, plan.id());
            executions.insert(record);
            return true;
        } catch (PlanTransactionGateway.AlreadyExecuted ignored) {
            return false;
        }
    }

    private boolean createFixed(InvestmentPlan plan, Instant businessDate) {
        try {
            transactions.createPending(plan.ownerId(), plan.portfolioFundId(), plan.amount(), businessDate, plan.id());
            return true;
        } catch (PlanTransactionGateway.AlreadyExecuted ignored) {
            return false;
        }
    }

    private InvestmentPlanExecution decisionRecord(InvestmentPlan plan, Instant businessDate,
                                                   PlanInvestmentFactsGateway.Facts facts,
                                                   SmartInvestmentAmountPolicy.Decision decision) {
        String reasonCode = decision.reasonCode() == null ? plan.amountStrategy().name() : decision.reasonCode();
        String reason = decision.reason() == null ? "按规则执行" : decision.reason();
        return new InvestmentPlanExecution(null, plan.id(), businessDate, plan.amountStrategy(),
                SmartInvestmentAmountPolicy.RULE_VERSION,
                decision.executable() ? InvestmentPlanExecution.Result.EXECUTED : InvestmentPlanExecution.Result.SKIPPED,
                reasonCode, reason, plan.amount(), decision.amount(), decision.rate(), facts.dataDate(),
                facts.referenceIndexCode(), facts.movingAverageDays(), decision.primaryMetric(),
                decision.secondaryMetric());
    }

    /** 本月内该计划是否已生成任意状态账目或智能决策；月定投据此避免月内重复执行。 */
    private boolean hasAnyOccurrenceThisMonth(InvestmentPlan plan, Instant businessDate) {
        Instant monthStart = InvestmentPlanForecastQueryHandler.monthStart(businessDate);
        Instant nextMonthStart = InvestmentPlanForecastQueryHandler.nextMonthStart(businessDate);
        boolean transactionExists = transactions.occurrences(plan.ownerId(), monthStart, nextMonthStart).stream()
                .anyMatch(occurrence -> occurrence.planId() == plan.id());
        return transactionExists || (plan.frequency() == InvestmentPlanFrequency.MONTHLY
                && executions.existsBetween(plan.id(), monthStart, nextMonthStart));
    }
}
