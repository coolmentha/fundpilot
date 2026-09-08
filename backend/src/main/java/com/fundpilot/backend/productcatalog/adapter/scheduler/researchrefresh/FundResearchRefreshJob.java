package com.fundpilot.backend.productcatalog.adapter.scheduler.researchrefresh;

import com.fundpilot.backend.productcatalog.application.command.researchrefresh.FundResearchCommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FundResearchRefreshJob {
    private final FundResearchCommandHandler commands;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Shanghai")
    public void refreshDaily() {
        commands.refreshTrackedBatch();
    }
}
