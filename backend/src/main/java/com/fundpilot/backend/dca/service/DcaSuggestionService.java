package com.fundpilot.backend.dca.service;

import com.fundpilot.backend.dca.entity.FundDcaPlanEntity;
import com.fundpilot.backend.dca.enums.DcaPlanStatus;
import com.fundpilot.backend.dca.repository.FundDcaPlanRepository;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** 单基金定投生成事务，供定时任务逐基金调用并隔离失败。 */
@Service
@RequiredArgsConstructor
public class DcaSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(DcaSuggestionService.class);
    private final FundDcaPlanRepository fundDcaPlanRepository;
    private final FundRepository fundRepository;
    private final FundTransactionRepository fundTransactionRepository;
    private final DcaScheduleService dcaScheduleService;

    @Transactional
    public boolean generateForFund(Long fundId, Instant now) {
        FundEntity fund = fundRepository.findById(fundId).orElse(null);
        if (fund == null) {
            return false;
        }
        FundDcaPlanEntity plan = fundDcaPlanRepository
                .findByFundEntity_IdAndStatus(fundId, DcaPlanStatus.EFFECTIVE).orElse(null);
        if (plan == null || Boolean.FALSE.equals(plan.getEnabled()) || !dcaScheduleService.isScheduledForDay(plan, now)) {
            return false;
        }

        Instant dayStart = dcaScheduleService.startOfBusinessDay(now);
        Instant dayEnd = dayStart.atZone(com.fundpilot.backend.common.ChinaTradingDate.ZONE)
                .plusDays(1).toInstant();
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

}
