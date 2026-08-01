package com.fundpilot.backend.investmentplan.application.command.planexecution;

import com.fundpilot.backend.investmentplan.application.gateway.planexecution.PlanTradingCalendarGateway;
import com.fundpilot.backend.investmentplan.application.gateway.planexecution.PlanTransactionGateway;
import com.fundpilot.backend.investmentplan.application.query.planexecution.InvestmentPlanForecastQueryHandler;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlan;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanRepository;
import com.fundpilot.backend.sharedkernel.BusinessDay;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 单计划执行事务；定时适配器逐计划调用以隔离失败。 */
@Service
@RequiredArgsConstructor
public class InvestmentPlanExecutionCommandHandler {
    private final InvestmentPlanRepository plans;
    private final PlanTradingCalendarGateway calendar;
    private final PlanTransactionGateway transactions;

    @Transactional
    public boolean execute(long planId, Instant now) {
        var plan = plans.findById(planId).orElse(null);
        Instant businessDate = BusinessDay.toDateLabel(now);
        if (plan == null || !calendar.isTradingDay(businessDate)) return false;
        boolean alreadyExecutedThisMonth = hasAnyOccurrenceThisMonth(plan, businessDate);
        Instant latestTradingDayBefore = calendar.latestBefore(businessDate).orElse(null);
        if (!plan.executableOn(businessDate, alreadyExecutedThisMonth, latestTradingDayBefore)) return false;
        try {
            transactions.createPending(plan.ownerId(), plan.portfolioFundId(), plan.amount(), businessDate, plan.id());
            return true;
        } catch (PlanTransactionGateway.AlreadyExecuted ignored) {
            return false;
        }
    }

    /** 本月内该计划是否已生成任意状态账目(含 CANCELLED 占位)；月定投据此避免月内重复执行。 */
    private boolean hasAnyOccurrenceThisMonth(InvestmentPlan plan, Instant businessDate) {
        Instant monthStart = InvestmentPlanForecastQueryHandler.monthStart(businessDate);
        Instant nextMonthStart = InvestmentPlanForecastQueryHandler.nextMonthStart(businessDate);
        return transactions.occurrences(plan.ownerId(), monthStart, nextMonthStart).stream()
                .anyMatch(occurrence -> occurrence.planId() == plan.id());
    }
}
