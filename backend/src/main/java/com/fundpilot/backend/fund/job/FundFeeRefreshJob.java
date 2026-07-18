package com.fundpilot.backend.fund.job;

import com.fundpilot.backend.fund.service.FundFeeService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 基金费率定时刷新任务。
 * <p>费率慢变(基金合同修改才改),每日北京时间 02:30 刷新一次持仓基金的费率即可，
 * 必须早于 03:00 NavConfirmJob,避免空缓存按零费率确认交易。
 * 不在启动线程预热:逐基金爬取受共享限流,会让启动时长随持仓数线性增长;
 * 缓存缺失时交易确认按既有规则降级零费率或按需获取。
 * 刷新失败不阻塞:记 warn,交易确认时降级为不扣费(fee=0)。
 */
@Component
@RequiredArgsConstructor
public class FundFeeRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(FundFeeRefreshJob.class);

    private final FundFeeService fundFeeService;

    /** 每日北京时间 02:30 刷新持仓基金费率。 */
    @Scheduled(cron = "0 30 2 * * *", zone = "Asia/Shanghai")
    public void refreshDaily() {
        try {
            fundFeeService.refreshHoldingFunds();
        } catch (RuntimeException e) {
            log.warn("基金费率每日刷新异常: {}", e.getMessage());
        }
    }

}
