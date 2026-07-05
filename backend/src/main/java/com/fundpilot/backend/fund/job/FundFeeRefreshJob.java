package com.fundpilot.backend.fund.job;

import com.fundpilot.backend.fund.service.FundFeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 基金费率定时刷新任务。
 * <p>费率慢变(基金合同修改才改),每日 06:30 刷新一次持仓基金的费率即可。
 * 启动时(ApplicationReadyEvent)预热一次,确保新部署后交易确认能拿到费率。
 * 刷新失败不阻塞:记 warn,交易确认时降级为不扣费(fee=0)。
 */
@Component
public class FundFeeRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(FundFeeRefreshJob.class);

    private final FundFeeService fundFeeService;

    public FundFeeRefreshJob(FundFeeService fundFeeService) {
        this.fundFeeService = fundFeeService;
    }

    /**
     * 每日 06:30(UTC)刷新持仓基金费率。
     * cron 六段式:秒 分 时 日 月 周。06:30 UTC = 14:30 北京时间,盘前刷新完毕。
     */
    @Scheduled(cron = "0 30 6 * * *")
    public void refreshDaily() {
        try {
            fundFeeService.refreshHoldingFunds();
        } catch (RuntimeException e) {
            log.warn("基金费率每日刷新异常: {}", e.getMessage());
        }
    }

    /**
     * 启动时预热:确保新部署后交易确认能拿到费率。
     * 失败不阻塞启动(交易确认时降级为不扣费)。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            fundFeeService.refreshHoldingFunds();
            log.info("基金费率启动预热完成");
        } catch (RuntimeException e) {
            log.warn("基金费率启动预热失败,交易确认将降级为不扣费: {}", e.getMessage());
        }
    }
}
