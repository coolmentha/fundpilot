package com.fundpilot.backend.dca.job;

import com.fundpilot.backend.common.ChinaTradingDate;
import com.fundpilot.backend.dca.repository.FundDcaPlanRepository;
import com.fundpilot.backend.dca.service.DcaSuggestionService;
import com.fundpilot.backend.fund.service.support.TradingCalendarService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
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
 * <p>幂等:同日同计划已有任意状态交易则跳过(确认或撤销后重跑也不重复生成)。
 */
@Component
@RequiredArgsConstructor
public class DcaSuggestionJob {

    private static final Logger log = LoggerFactory.getLogger(DcaSuggestionJob.class);
    private final FundDcaPlanRepository fundDcaPlanRepository;
    private final TradingCalendarService tradingCalendarService;
    private final DcaSuggestionService dcaSuggestionService;
    private final Clock clock;

    @Scheduled(cron = "0 55 14 * * MON-FRI", zone = "Asia/Shanghai")
    public void run() {
        Instant now = clock.instant();
        Instant todayUtc = ChinaTradingDate.toUtcDate(now);
        if (!tradingCalendarService.isTradingDay(todayUtc)) {
            return;
        }
        List<Long> fundIds = fundDcaPlanRepository.findEffectiveFundIds();
        int generated = 0;
        for (Long fundId : fundIds) {
            try {
                if (dcaSuggestionService.generateForFund(fundId, now)) {
                    generated++;
                }
            } catch (RuntimeException ex) {
                log.error("定投建议生成失败 fund_id={} : {}", fundId, ex.getMessage(), ex);
            }
        }
        log.info("定投建议任务结束 generated={}/{}", generated, fundIds.size());
    }

}
