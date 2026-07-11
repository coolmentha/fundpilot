package com.fundpilot.backend.fund.job;

import com.fundpilot.backend.fund.service.FundDictBackfillService;
import com.fundpilot.backend.fund.service.FundDictSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 基金字典同步定时任务(ADR-0005):每日凌晨拉全量东方财富字典,upsert 到 {@code fund_dict} 表。
 * <p>字典变化频率低(新基金成立才增量),每日一次足够。凌晨 3 点执行避开行情拉取高峰。
 * cron {@code 0 0 3 * * *} = 每天北京时间 03:00:00。
 */
@Component
@RequiredArgsConstructor
public class FundDictSyncJob {

    private final FundDictSyncService fundDictSyncService;
    private final FundDictBackfillService fundDictBackfillService;

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Shanghai")
    public void syncDaily() {
        fundDictSyncService.syncAll();
        fundDictBackfillService.backfillAll();
    }
}
