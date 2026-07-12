package com.fundpilot.backend.dca.service;

import com.fundpilot.backend.dca.entity.FundDcaPlanEntity;
import com.fundpilot.backend.dca.enums.DcaFrequency;
import com.fundpilot.backend.dca.enums.DcaPlanStatus;
import com.fundpilot.backend.dca.repository.FundDcaPlanRepository;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.fund.service.support.TradingCalendarService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** 单基金定投生成事务，供定时任务逐基金调用并隔离失败。 */
@Service
@RequiredArgsConstructor
public class DcaSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(DcaSuggestionService.class);
    private static final ZoneId TRADING_ZONE = ZoneId.of("Asia/Shanghai");

    private final FundDcaPlanRepository fundDcaPlanRepository;
    private final FundRepository fundRepository;
    private final FundTransactionRepository fundTransactionRepository;
    private final TradingCalendarService tradingCalendarService;

    @Transactional
    public boolean generateForFund(Long fundId, Instant now) {
        FundEntity fund = fundRepository.findById(fundId).orElse(null);
        if (fund == null) {
            return false;
        }
        FundDcaPlanEntity plan = fundDcaPlanRepository
                .findByFundEntity_IdAndStatus(fundId, DcaPlanStatus.EFFECTIVE).orElse(null);
        if (plan == null || Boolean.FALSE.equals(plan.getEnabled()) || !isDcaDay(plan, now)) {
            return false;
        }

        ZonedDateTime todayStart = now.atZone(TRADING_ZONE).toLocalDate().atStartOfDay(TRADING_ZONE);
        Instant dayStart = todayStart.toInstant();
        Instant dayEnd = todayStart.plusDays(1).toInstant();
        if (fundTransactionRepository.existsByDcaPlanIdAndTradeDateBetween(
                plan.getId(), dayStart, dayEnd)) {
            return false;
        }

        int inserted = fundTransactionRepository.insertDcaPendingIfAbsent(
                fund.getId(), plan.getAmount(), now, plan.getId());
        if (inserted == 0) {
            return false;
        }
        log.info("定投交易生成 fund_id={} plan_id={} amount={}", fundId, plan.getId(), plan.getAmount());
        return true;
    }

    boolean isDcaDay(FundDcaPlanEntity plan, Instant now) {
        ZonedDateTime today = now.atZone(TRADING_ZONE);
        if (plan.getFrequency() == DcaFrequency.DAILY) {
            return true;
        }
        if (plan.getFrequency() == DcaFrequency.WEEKLY) {
            int todayDow = today.getDayOfWeek().getValue();
            return plan.getDayOfWeek() != null && todayDow == plan.getDayOfWeek();
        }
        if (plan.getFrequency() == DcaFrequency.MONTHLY) {
            LocalDate todayDate = today.toLocalDate();
            int planDom = plan.getDayOfMonth();
            LocalDate scheduledDate = todayDate.getDayOfMonth() >= planDom
                    ? todayDate.withDayOfMonth(planDom)
                    : todayDate.minusMonths(1).withDayOfMonth(planDom);
            for (LocalDate date = scheduledDate; date.isBefore(todayDate); date = date.plusDays(1)) {
                Instant checkDay = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
                if (tradingCalendarService.isTradingDay(checkDay)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
