package com.fundpilot.backend.investmentplan.application.query.planexecution;

import com.fundpilot.backend.investmentplan.application.gateway.planexecution.PlanTradingCalendarGateway;
import com.fundpilot.backend.investmentplan.application.gateway.planexecution.PlanTransactionGateway;
import com.fundpilot.backend.investmentplan.domain.execution.InvestmentPlanExecutionRepository;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlan;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanStatus;
import com.fundpilot.backend.sharedkernel.BusinessDay;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 定投计划列表与预算摘要共用的本月剩余执行预测。 */
@Service
@RequiredArgsConstructor
public class InvestmentPlanForecastQueryHandler {
    private static final LocalTime EXECUTION_TIME = LocalTime.of(14, 55);

    private final PlanTradingCalendarGateway calendar;
    private final PlanTransactionGateway transactions;
    private final Clock clock;
    private final InvestmentPlanExecutionRepository executions;

    @Transactional(readOnly = true)
    public Map<Long, List<Instant>> currentMonthExecutionDates(long ownerId, List<InvestmentPlan> plans) {
        Instant now = clock.instant();
        Instant monthStart = monthStart(now);
        Instant monthEnd = nextMonthStart(now);
        List<InvestmentPlan> activePlans = plans.stream()
                .filter(plan -> plan.enabled() && plan.status() == InvestmentPlanStatus.EFFECTIVE)
                .toList();
        if (activePlans.isEmpty()) return Map.of();

        Map<Long, List<Instant>> datesByPlan = new HashMap<>();
        Set<Key> occupied = new HashSet<>();
        Set<Long> occupiedPlanIds = new HashSet<>();
        for (var occurrence : transactions.occurrences(ownerId, monthStart, monthEnd)) {
            occupied.add(new Key(occurrence.planId(), BusinessDay.toDateLabel(occurrence.tradeDate())));
            occupiedPlanIds.add(occurrence.planId());
        }
        for (var execution : executions.findBetween(activePlans.stream().map(InvestmentPlan::id).toList(),
                monthStart, monthEnd)) {
            occupied.add(new Key(execution.planId(), BusinessDay.toDateLabel(execution.businessDate())));
            occupiedPlanIds.add(execution.planId());
        }
        Instant today = BusinessDay.toDateLabel(now);
        boolean todayPendingExecution = now.atZone(BusinessDay.ZONE).toLocalTime().isBefore(EXECUTION_TIME);
        Set<Long> scheduledThisMonth = new HashSet<>();
        Instant previousTradingDay = calendar.latestBefore(monthStart).orElse(null);
        for (Instant day : calendar.tradingDaysBetween(monthStart, monthEnd)) {
            for (InvestmentPlan plan : activePlans) {
                boolean alreadyExecuted = occupiedPlanIds.contains(plan.id())
                        || scheduledThisMonth.contains(plan.id());
                if ((day.isAfter(today) || (day.equals(today) && todayPendingExecution))
                        && plan.executableOn(day, alreadyExecuted, previousTradingDay)
                        && !occupied.contains(new Key(plan.id(), day))) {
                    datesByPlan.computeIfAbsent(plan.id(), ignored -> new java.util.ArrayList<>()).add(day);
                    if (plan.frequency() == com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanFrequency.MONTHLY) {
                        scheduledThisMonth.add(plan.id());
                    }
                }
            }
            previousTradingDay = day;
        }
        datesByPlan.replaceAll((ignored, dates) -> List.copyOf(dates));
        return Map.copyOf(datesByPlan);
    }

    public static Instant monthStart(Instant instant) {
        var date = instant.atZone(BusinessDay.ZONE).toLocalDate().withDayOfMonth(1);
        return date.atStartOfDay(BusinessDay.ZONE).withZoneSameLocal(java.time.ZoneOffset.UTC).toInstant();
    }

    public static Instant nextMonthStart(Instant instant) {
        var date = instant.atZone(BusinessDay.ZONE).toLocalDate().withDayOfMonth(1).plusMonths(1);
        return date.atStartOfDay(BusinessDay.ZONE).withZoneSameLocal(java.time.ZoneOffset.UTC).toInstant();
    }

    private record Key(long planId, Instant date) {}
}
