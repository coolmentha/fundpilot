package com.fundpilot.backend.fund.job;

import com.fundpilot.backend.fund.service.FundDictBackfillService;
import com.fundpilot.backend.fund.service.FundDictSyncService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 基金字典同步定时任务(ADR-0005):每日凌晨拉全量东方财富字典,upsert 到 {@code fund_dict} 表。
 * <p>字典变化频率低(新基金成立才增量),每日一次足够。凌晨 3 点执行避开行情拉取高峰。
 * cron {@code 0 0 3 * * *} = 每天 03:00:00(服务器本地时区)。
 */
@Component
public class FundDictSyncJob {

    private final FundDictSyncService fundDictSyncService;
    private final FundDictBackfillService fundDictBackfillService;

    public FundDictSyncJob(FundDictSyncService fundDictSyncService,
                           FundDictBackfillService fundDictBackfillService) {
        this.fundDictSyncService = fundDictSyncService;
        this.fundDictBackfillService = fundDictBackfillService;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void syncDaily() {
        fundDictSyncService.syncAll();
        fundDictBackfillService.backfillAll();
    }
}
