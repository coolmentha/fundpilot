package com.fundpilot.backend.fund.job;

import com.fundpilot.backend.fund.service.DcaService;
import com.fundpilot.backend.market.repository.TradingCalendarRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 定投扣款定时任务(issue #61):每日工作日 14:30 触发,仅当当日为当月最后一个交易日时真正扣款。
 * <p>cron {@code 0 30 14 * * MON-FRI}(工作日 14:30)。月份最后交易日判定走 {@link TradingCalendarRepository#isLastTradingDayOfMonth},
 * 避免月末逢周末漏扣(单纯用 L + MON-FRI 会在月末为周末的月份整月不扣款)。
 * 金额 = 基金级 dcaAmount;份额留空由 {@code NavConfirmJob} 按当日净值回填并加权更新 costPerShare。
 * 行业止盈暂停期跳过,宽基一直定投。依赖 {@code @EnableScheduling}(见启动类)。
 */
@Component
@RequiredArgsConstructor
public class DcaJob {

    private static final Logger log = LoggerFactory.getLogger(DcaJob.class);

    private final DcaService dcaService;
    private final TradingCalendarRepository tradingCalendarRepository;

    @Scheduled(cron = "0 30 14 * * MON-FRI")
    public void run() {
        Instant today = Instant.now();
        if (!tradingCalendarRepository.isLastTradingDayOfMonth(today)) {
            log.debug("今日非当月最后交易日,跳过定投扣款 today={}", today);
            return;
        }
        log.info("定投扣款任务开始(当月最后交易日) today={}", today);
        int produced = dcaService.investMonthly();
        log.info("定投扣款任务结束 produced={}", produced);
    }
}