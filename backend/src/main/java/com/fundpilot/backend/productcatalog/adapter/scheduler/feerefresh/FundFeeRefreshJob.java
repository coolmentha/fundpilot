package com.fundpilot.backend.productcatalog.adapter.scheduler.feerefresh;

import com.fundpilot.backend.productcatalog.application.command.feerefresh.FundFeeCommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FundFeeRefreshJob {
    private final FundFeeCommandHandler commands;

    @Scheduled(cron = "0 30 2 * * *", zone = "Asia/Shanghai")
    public void refreshDaily() {
        commands.refreshKnownSchedules();
    }
}
