package com.fundpilot.backend.portfolio.job;

import com.fundpilot.backend.common.ChinaTradingDate;
import com.fundpilot.backend.fund.service.support.TradingCalendarService;
import com.fundpilot.backend.portfolio.service.PortfolioReturnTrendService;
import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi;
import com.fundpilot.backend.identityaccess.adapter.api.userdirectory.UserDirectoryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@RequiredArgsConstructor
public class PortfolioReturnSnapshotJob {
    private final PortfolioReturnTrendService trendService;
    private final TradingCalendarService tradingCalendarService;
    private final Clock clock;
    private final UserDirectoryApi users;
    private final CurrentActorApi actors;

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Shanghai")
    public void capturePreviousTradingDay() {
        tradingCalendarService.latestTradingDayBefore(ChinaTradingDate.toUtcDate(clock.instant()))
                .ifPresent(businessDate -> users.activeUserIds().forEach(userId ->
                        actors.runAsSystem(userId, () -> trendService.capture(businessDate))));
    }

    /** QDII 净值可能在早间补拉，重复 upsert 覆盖同一业务日期。 */
    @Scheduled(cron = "0 */30 4-10 * * *", zone = "Asia/Shanghai")
    public void recaptureAfterNavCatchUp() {
        capturePreviousTradingDay();
    }
}
