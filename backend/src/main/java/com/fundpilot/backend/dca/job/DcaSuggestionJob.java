package com.fundpilot.backend.dca.job;

import com.fundpilot.backend.common.ChinaTradingDate;
import com.fundpilot.backend.dca.entity.FundDcaPlanEntity;
import com.fundpilot.backend.dca.enums.DcaFrequency;
import com.fundpilot.backend.dca.enums.DcaPlanStatus;
import com.fundpilot.backend.dca.repository.FundDcaPlanRepository;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.fund.service.support.TradingCalendarService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 定投建议定时任务:每个交易日 14:55 遍历所有 EFFECTIVE 定投计划,
 * 判定今天是否是定投日,命中则自动生成 source=INVEST 的 PENDING 交易(由 NavConfirmJob 凌晨 3 点确认)。
 *
 * <p>定投日判定:
 * <ul>
 *   <li>非交易日跳过(TradingCalendarService.isTradingDay)</li>
 *   <li>周定投:today 的 day-of-week(1=周一) == plan.dayOfWeek</li>
 *   <li>月定投:today 的 day-of-month == plan.dayOfMonth;若计划日非交易日,顺延到下一个交易日补执行</li>
 * </ul>
 *
 * <p>幂等:同日同计划已有 PENDING 交易则跳过(防重跑重复生成)。
 */
@Component
@RequiredArgsConstructor
public class DcaSuggestionJob {

    private static final Logger log = LoggerFactory.getLogger(DcaSuggestionJob.class);
    private static final ZoneId TRADING_ZONE = ZoneId.of("Asia/Shanghai");

    private final FundDcaPlanRepository fundDcaPlanRepository;
    private final FundRepository fundRepository;
    private final FundTransactionRepository fundTransactionRepository;
    private final TradingCalendarService tradingCalendarService;

    @Scheduled(cron = "0 55 14 * * MON-FRI", zone = "Asia/Shanghai")
    public void run() {
        Instant now = Instant.now();
        Instant todayUtc = ChinaTradingDate.toUtcDate(now);
        if (!tradingCalendarService.isTradingDay(todayUtc)) {
            return;
        }
        List<Long> fundIds = fundDcaPlanRepository.findEffectiveFundIds();
        int generated = 0;
        for (Long fundId : fundIds) {
            try {
                if (generateForFund(fundId, now)) {
                    generated++;
                }
            } catch (RuntimeException ex) {
                log.error("定投建议生成失败 fund_id={} : {}", fundId, ex.getMessage(), ex);
            }
        }
        log.info("定投建议任务结束 generated={}/{}", generated, fundIds.size());
    }

    @Transactional
    public boolean generateForFund(Long fundId, Instant now) {
        FundEntity fund = fundRepository.findById(fundId).orElse(null);
        if (fund == null) return false;
        FundDcaPlanEntity plan = fundDcaPlanRepository
                .findByFundEntity_IdAndStatus(fundId, DcaPlanStatus.EFFECTIVE).orElse(null);
        if (plan == null || Boolean.FALSE.equals(plan.getEnabled())) return false;
        if (!isDcaDay(plan, now)) return false;

        // 幂等去重:同日同计划已有 PENDING 交易则跳过
        ZonedDateTime todayStart = now.atZone(TRADING_ZONE).toLocalDate().atStartOfDay(TRADING_ZONE);
        Instant dayStart = todayStart.toInstant();
        Instant dayEnd = todayStart.plusDays(1).toInstant();
        if (fundTransactionRepository.existsByDcaPlanIdAndStatusAndCreatedDateBetween(
                plan.getId(), FundTransactionStatus.PENDING, dayStart, dayEnd)) {
            return false;
        }

        FundTransactionEntity tx = new FundTransactionEntity();
        tx.setFundEntity(fund);
        tx.setSource(FundTransactionSource.INVEST);
        tx.setAmount(plan.getAmount());
        tx.setShares(null);
        tx.setNav(null);
        tx.setStatus(FundTransactionStatus.PENDING);
        tx.setDcaPlanId(plan.getId());
        fundTransactionRepository.save(tx);
        log.info("定投交易生成 fund_id={} plan_id={} amount={}", fundId, plan.getId(), plan.getAmount());
        return true;
    }

    /**
     * 判定 today 是否是定投日。
     * 周定投:比对 day-of-week。月定投:比对 day-of-month,计划日遇节假日顺延到下一个交易日。
     */
    boolean isDcaDay(FundDcaPlanEntity plan, Instant now) {
        ZonedDateTime today = now.atZone(TRADING_ZONE);
        int todayDom = today.getDayOfMonth();
        if (plan.getFrequency() == DcaFrequency.DAILY) {
            return true; // run() 已 gating 交易日,每个交易日都投
        }
        if (plan.getFrequency() == DcaFrequency.WEEKLY) {
            int todayDow = today.getDayOfWeek().getValue(); // 1=周一
            return plan.getDayOfWeek() != null && todayDow == plan.getDayOfWeek();
        }
        if (plan.getFrequency() == DcaFrequency.MONTHLY) {
            int planDom = plan.getDayOfMonth();
            if (planDom == todayDom) return true;
            if (planDom > todayDom) return false;
            // planDom < todayDom:计划日已过,检查是否因节假日顺延到今天
            // 顺延条件:planDom..today-1 区间全是非交易日(节假日),且今天是该区间后第一个交易日
            for (int d = planDom; d < todayDom; d++) {
                Instant checkDay = ChinaTradingDate.toUtcDate(today.withDayOfMonth(d).toInstant());
                if (tradingCalendarService.isTradingDay(checkDay)) {
                    return false; // planDom..today-1 之间有交易日,说明计划日是交易日已过,不补
                }
            }
            return true; // planDom..today-1 全是非交易日,今天补执行
        }
        return false;
    }
}
