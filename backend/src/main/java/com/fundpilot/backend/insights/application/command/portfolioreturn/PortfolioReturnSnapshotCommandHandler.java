package com.fundpilot.backend.insights.application.command.portfolioreturn;

import com.fundpilot.backend.insights.application.query.portfolioreturn.PortfolioReturnQueryHandler;
import com.fundpilot.backend.insights.application.gateway.portfolioreturn.ReturnSnapshotSchedulingGateway;
import com.fundpilot.backend.insights.domain.portfolioreturn.PortfolioReturnSnapshot;
import com.fundpilot.backend.insights.domain.portfolioreturn.PortfolioReturnSnapshotRepository;
import java.time.Clock;
import java.time.Instant;
import com.fundpilot.backend.sharedkernel.BusinessDay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioReturnSnapshotCommandHandler {
    private final PortfolioReturnSnapshotRepository snapshots;
    private final PortfolioReturnQueryHandler returns;
    private final Clock clock;
    private final ReturnSnapshotSchedulingGateway scheduling;

    public void capturePreviousTradingDay() {
        scheduling.latestTradingDayBefore(BusinessDay.toDateLabel(clock.instant())).ifPresent(businessDate ->
                // 单用户失败只跳过该用户，不得连坐整个批次(issue #182)
                scheduling.activeOwnerIds().forEach(ownerId -> {
                    try {
                        scheduling.runAsSystem(ownerId, () -> capture(ownerId, businessDate));
                    } catch (RuntimeException exception) {
                        log.error("组合收益快照失败 owner={} date={}: {}", ownerId, businessDate,
                                exception.getMessage(), exception);
                    }
                }));
    }

    @Transactional
    public void capture(long ownerId, Instant businessDate) {
        var result = returns.findByOwner(ownerId);
        if (result.holdingAmount() == null) {
            // 持仓市值未知(估值拉取失败等)时按 CONTEXT 不拿旧净值冒充，跳过快照避免写 NOT NULL 违约(issue #182)
            log.warn("持仓市值未知，跳过快照 owner={} date={}", ownerId, businessDate);
            return;
        }
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
