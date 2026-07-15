package com.fundpilot.backend.dca.service;

import com.fundpilot.backend.common.ChinaTradingDate;
import com.fundpilot.backend.dca.entity.FundDcaPlanEntity;
import com.fundpilot.backend.dca.enums.DcaPlanStatus;
import com.fundpilot.backend.dca.repository.FundDcaPlanRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 统一计算各计划本月尚未生成交易的预计执行日期。 */
@Service
@RequiredArgsConstructor
public class DcaPlanForecastService {

    private final FundDcaPlanRepository fundDcaPlanRepository;
    private final FundTransactionRepository fundTransactionRepository;
    private final DcaScheduleService dcaScheduleService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public BigDecimal currentMonthRemainingAmount() {
        List<FundDcaPlanEntity> plans = fundDcaPlanRepository.findAllWithFund();
        Map<Long, List<Instant>> datesByPlan = currentMonthExecutionDates(plans);
        return plans.stream()
                .map(plan -> plan.getAmount().multiply(BigDecimal.valueOf(
                        datesByPlan.getOrDefault(plan.getId(), List.of()).size())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<Instant>> currentMonthExecutionDates(List<FundDcaPlanEntity> plans) {
        List<FundDcaPlanEntity> activePlans = plans.stream()
                .filter(plan -> plan.getStatus() == DcaPlanStatus.EFFECTIVE)
                .filter(plan -> Boolean.TRUE.equals(plan.getEnabled()))
                .toList();
        if (activePlans.isEmpty()) {
            return Map.of();
        }

        Instant now = clock.instant();
        Instant dayStart = dcaScheduleService.startOfBusinessDay(now);
        Instant monthEnd = dcaScheduleService.startOfNextMonth(now);
        Set<DcaOccurrence> existing = existingOccurrences(activePlans, dayStart, monthEnd);
        Map<Long, List<Instant>> datesByPlan = new HashMap<>();
        ZonedDateTime cursor = dayStart.atZone(ChinaTradingDate.ZONE);
        ZonedDateTime end = monthEnd.atZone(ChinaTradingDate.ZONE);
        while (cursor.isBefore(end)) {
            Instant candidate = cursor.toInstant();
            Instant businessDate = ChinaTradingDate.toUtcDate(candidate);
            for (FundDcaPlanEntity plan : activePlans) {
                DcaOccurrence occurrence = new DcaOccurrence(plan.getId(), businessDate);
                if (!existing.contains(occurrence)
                        && dcaScheduleService.isFutureExecutionDay(plan, candidate, now)) {
                    datesByPlan.computeIfAbsent(plan.getId(), ignored -> new java.util.ArrayList<>())
                            .add(businessDate);
                }
            }
            cursor = cursor.plusDays(1);
        }
        datesByPlan.replaceAll((ignored, dates) -> List.copyOf(dates));
        return Map.copyOf(datesByPlan);
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

    private record DcaOccurrence(Long planId, Instant businessDate) {
    }
}
