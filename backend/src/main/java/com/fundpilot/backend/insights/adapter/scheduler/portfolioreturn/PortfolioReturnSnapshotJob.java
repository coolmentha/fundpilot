package com.fundpilot.backend.insights.adapter.scheduler.portfolioreturn;

import com.fundpilot.backend.insights.application.command.portfolioreturn.PortfolioReturnSnapshotCommandHandler;
import com.fundpilot.backend.platform.observability.JobName;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fundpilot.insights-snapshot.enabled", havingValue = "true", matchIfMissing = true)
public class PortfolioReturnSnapshotJob {
    private final PortfolioReturnSnapshotCommandHandler snapshots;

    @JobName("组合收益快照")
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Shanghai")
    public void capturePreviousTradingDay() {
        snapshots.capturePreviousTradingDay();
    }

    @JobName("组合收益快照补采")
    @Scheduled(cron = "0 */30 4-10 * * *", zone = "Asia/Shanghai")
    public void recaptureAfterNavCatchUp() {
        capturePreviousTradingDay();
    }
}
