package com.fundpilot.backend.insights.application.command.portfolioreturn;

import com.fundpilot.backend.insights.application.query.portfolioreturn.PortfolioReturnQueryHandler;
import com.fundpilot.backend.insights.application.gateway.portfolioreturn.ReturnSnapshotSchedulingGateway;
import com.fundpilot.backend.insights.domain.portfolioreturn.PortfolioReturnSnapshot;
import com.fundpilot.backend.insights.domain.portfolioreturn.PortfolioReturnSnapshotRepository;
import java.time.Clock;
import java.time.Instant;
import com.fundpilot.backend.sharedkernel.BusinessDay;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PortfolioReturnSnapshotCommandHandler {
    private final PortfolioReturnSnapshotRepository snapshots;
    private final PortfolioReturnQueryHandler returns;
    private final Clock clock;
    private final ReturnSnapshotSchedulingGateway scheduling;

    public void capturePreviousTradingDay() {
        scheduling.latestTradingDayBefore(BusinessDay.toDateLabel(clock.instant())).ifPresent(businessDate ->
                scheduling.activeOwnerIds().forEach(ownerId -> scheduling.runAsSystem(ownerId,
                        () -> capture(ownerId, businessDate))));
    }

    @Transactional
    public void capture(long ownerId, Instant businessDate) {
        var result = returns.findByOwner(ownerId);
        var missing = result.funds().stream()
                .filter(fund -> fund.investedAmount().signum() > 0 && fund.unrealizedPnl() == null)
                .map(PortfolioReturnQueryHandler.FundReturnResult::fundCode).sorted().toList();
        Long id = snapshots.find(ownerId, businessDate).map(PortfolioReturnSnapshot::id).orElse(null);
        Instant now = clock.instant();
        snapshots.save(new PortfolioReturnSnapshot(id, ownerId, businessDate, result.investedAmount(),
                result.redeemedAmount(), result.feeAmount(), result.holdingAmount(), result.realizedPnl(),
                result.unrealizedPnl(), result.totalReturn(), missing.isEmpty(), String.join(",", missing), now));
    }
}
