package com.fundpilot.backend.dca.service;

import com.fundpilot.backend.common.ChinaTradingDate;
import com.fundpilot.backend.dca.controller.DcaBudgetSummaryView;
import com.fundpilot.backend.dca.entity.FundDcaPlanEntity;
import com.fundpilot.backend.dca.enums.DcaPlanStatus;
import com.fundpilot.backend.dca.repository.FundDcaPlanRepository;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.user.service.UserConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 月度定投预算的唯一统计口径。预算只提示，不参与任何交易确认或计划生成。
 */
@Service
@RequiredArgsConstructor
public class DcaBudgetSummaryService {

    private final UserConfigService userConfigService;
    private final FundDcaPlanRepository fundDcaPlanRepository;
    private final FundTransactionRepository fundTransactionRepository;
    private final DcaScheduleService dcaScheduleService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DcaBudgetSummaryView currentMonth() {
        Instant now = clock.instant();
        Instant monthStart = dcaScheduleService.startOfCurrentMonth(now);
        Instant monthEnd = dcaScheduleService.startOfNextMonth(now);
        BigDecimal investedAmount = nonNull(fundTransactionRepository.sumAmountBySourceAndStatusNotAndTradeDateBetween(
                FundTransactionSource.INVEST, FundTransactionStatus.CANCELLED, monthStart, monthEnd));

        List<FundDcaPlanEntity> plans = fundDcaPlanRepository
                .findByStatusAndEnabledTrue(DcaPlanStatus.EFFECTIVE);
        BigDecimal futureAmount = calculateFutureAmount(plans, now, monthEnd);
        BigDecimal projectedAmount = investedAmount.add(futureAmount);
        BigDecimal monthlyBudget = userConfigService.getMonthlyDcaBudget();
        BigDecimal remainingAmount = null;
        BigDecimal overBudgetAmount = null;
        if (monthlyBudget != null) {
            BigDecimal difference = monthlyBudget.subtract(projectedAmount);
            remainingAmount = difference.signum() >= 0 ? difference : BigDecimal.ZERO;
            overBudgetAmount = difference.signum() < 0 ? difference.negate() : BigDecimal.ZERO;
        }
        return new DcaBudgetSummaryView(monthlyBudget, investedAmount, futureAmount, projectedAmount,
                remainingAmount, overBudgetAmount);
    }

    private BigDecimal calculateFutureAmount(List<FundDcaPlanEntity> plans, Instant now, Instant monthEnd) {
        if (plans.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Instant dayStart = dcaScheduleService.startOfBusinessDay(now);
        Set<DcaOccurrence> existing = existingOccurrences(plans, dayStart, monthEnd);
        BigDecimal futureAmount = BigDecimal.ZERO;
        ZonedDateTime cursor = dayStart.atZone(ChinaTradingDate.ZONE);
        ZonedDateTime end = monthEnd.atZone(ChinaTradingDate.ZONE);
        while (cursor.isBefore(end)) {
            Instant candidate = cursor.toInstant();
            Instant businessDate = ChinaTradingDate.toUtcDate(candidate);
            for (FundDcaPlanEntity plan : plans) {
                DcaOccurrence occurrence = new DcaOccurrence(plan.getId(), businessDate);
                if (!existing.contains(occurrence)
                        && dcaScheduleService.isFutureExecutionDay(plan, candidate, now)) {
                    futureAmount = futureAmount.add(plan.getAmount());
                }
            }
            cursor = cursor.plusDays(1);
        }
        return futureAmount;
    }

    private Set<DcaOccurrence> existingOccurrences(Collection<FundDcaPlanEntity> plans,
                                                    Instant start, Instant end) {
        List<Long> planIds = plans.stream().map(FundDcaPlanEntity::getId).toList();
        Set<DcaOccurrence> occurrences = new HashSet<>();
        for (FundTransactionRepository.DcaTransactionDateProjection transaction
                : fundTransactionRepository.findDcaTransactionDates(planIds, start, end)) {
            occurrences.add(new DcaOccurrence(transaction.getDcaPlanId(),
                    ChinaTradingDate.toUtcDate(transaction.getTradeDate())));
        }
        return occurrences;
    }

    private BigDecimal nonNull(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    private record DcaOccurrence(Long planId, Instant businessDate) {
    }
}
