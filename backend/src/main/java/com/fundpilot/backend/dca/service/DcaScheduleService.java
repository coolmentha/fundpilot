package com.fundpilot.backend.dca.service;

import com.fundpilot.backend.common.ChinaTradingDate;
import com.fundpilot.backend.dca.entity.FundDcaPlanEntity;
import com.fundpilot.backend.dca.enums.DcaFrequency;
import com.fundpilot.backend.fund.service.support.TradingCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/**
 * 定投执行日规则的唯一入口。Job 和月度预算预测必须共用，避免月计划跨月顺延出现不同结果。
 */
@Service
@RequiredArgsConstructor
public class DcaScheduleService {

    private static final LocalTime EXECUTION_TIME = LocalTime.of(14, 55);

    private final TradingCalendarService tradingCalendarService;

    /**
     * 判断某个交易日是否命中计划本身的周期规则。
     * 调用方负责先确认当天是交易日，保持 DcaSuggestionJob 现有的职责边界。
     */
    public boolean isScheduledForDay(FundDcaPlanEntity plan, Instant instant) {
        if (plan.getFrequency() == null) {
            return false;
        }
        ZonedDateTime today = instant.atZone(ChinaTradingDate.ZONE);
        if (plan.getFrequency() == DcaFrequency.DAILY) {
            return true;
        }
        if (plan.getFrequency() == DcaFrequency.WEEKLY) {
            return plan.getDayOfWeek() != null
                    && today.getDayOfWeek().getValue() == plan.getDayOfWeek();
        }
        if (plan.getFrequency() == DcaFrequency.MONTHLY) {
            if (plan.getDayOfMonth() == null) {
                return false;
            }
            LocalDate todayDate = today.toLocalDate();
            int planDay = plan.getDayOfMonth();
            LocalDate scheduledDate = todayDate.getDayOfMonth() >= planDay
                    ? todayDate.withDayOfMonth(planDay)
                    : todayDate.minusMonths(1).withDayOfMonth(planDay);
            for (LocalDate date = scheduledDate; date.isBefore(todayDate); date = date.plusDays(1)) {
                if (tradingCalendarService.isTradingDay(toBusinessDate(date))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * 判断计划是否会在当前时刻之后的候选业务日执行。
     * 当天仅在 14:55 前视为未来，已生成或已取消的同日交易由摘要服务另行排除。
     */
    public boolean isFutureExecutionDay(FundDcaPlanEntity plan, Instant candidate, Instant now) {
        if (!tradingCalendarService.isTradingDay(ChinaTradingDate.toUtcDate(candidate))
                || !isScheduledForDay(plan, candidate)) {
            return false;
        }
        ZonedDateTime candidateTime = candidate.atZone(ChinaTradingDate.ZONE);
        ZonedDateTime currentTime = now.atZone(ChinaTradingDate.ZONE);
        if (candidateTime.toLocalDate().isAfter(currentTime.toLocalDate())) {
            return true;
        }
        return candidateTime.toLocalDate().equals(currentTime.toLocalDate())
                && currentTime.toLocalTime().isBefore(EXECUTION_TIME);
    }

    public Instant startOfBusinessDay(Instant instant) {
        return instant.atZone(ChinaTradingDate.ZONE).toLocalDate()
                .atStartOfDay(ChinaTradingDate.ZONE).toInstant();
    }

    public Instant startOfCurrentMonth(Instant instant) {
        return instant.atZone(ChinaTradingDate.ZONE).toLocalDate().withDayOfMonth(1)
                .atStartOfDay(ChinaTradingDate.ZONE).toInstant();
    }

    public Instant startOfNextMonth(Instant instant) {
        return instant.atZone(ChinaTradingDate.ZONE).toLocalDate().withDayOfMonth(1)
                .plusMonths(1).atStartOfDay(ChinaTradingDate.ZONE).toInstant();
    }

    private Instant toBusinessDate(LocalDate date) {
        return date.atStartOfDay(ChinaTradingDate.ZONE).withZoneSameLocal(java.time.ZoneOffset.UTC).toInstant();
    }
}
