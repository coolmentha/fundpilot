package com.fundpilot.backend.insights.infrastructure.gateway.portfolioreturn;

import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi;
import com.fundpilot.backend.identityaccess.adapter.api.userdirectory.UserDirectoryApi;
import com.fundpilot.backend.insights.application.gateway.portfolioreturn.ReturnSnapshotSchedulingGateway;
import com.fundpilot.backend.marketdata.adapter.api.tradingcalendar.TradingCalendarApi;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReturnSnapshotSchedulingGatewayImpl implements ReturnSnapshotSchedulingGateway {
    private final TradingCalendarApi calendar;
    private final UserDirectoryApi users;
    private final CurrentActorApi actors;

    @Override public Optional<Instant> latestTradingDayBefore(Instant date) { return calendar.latestBefore(date); }
    @Override public List<Long> activeOwnerIds() { return users.activeUserIds(); }
    @Override public void runAsSystem(long ownerId, Runnable action) { actors.runAsSystem(ownerId, action); }
}
