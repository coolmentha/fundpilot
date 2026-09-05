package com.fundpilot.backend.insights.application.command.portfolioreturn;

import com.fundpilot.backend.insights.application.query.portfolioreturn.PortfolioReturnQueryHandler;
import com.fundpilot.backend.insights.application.gateway.portfolioreturn.ReturnSnapshotSchedulingGateway;
import com.fundpilot.backend.insights.domain.portfolioreturn.PortfolioReturnSnapshot;
import com.fundpilot.backend.insights.domain.portfolioreturn.PortfolioReturnSnapshotRepository;
import java.time.Clock;
import java.time.Instant;
import java.math.BigDecimal;
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
    public void recaptureExistingFrom(long ownerId, Instant fromBusinessDate) {
        snapshots.between(ownerId, fromBusinessDate, BusinessDay.toDateLabel(clock.instant())).stream()
                .map(PortfolioReturnSnapshot::businessDate)
                .forEach(businessDate -> capture(ownerId, businessDate));
    }

    @Transactional
    public void capture(long ownerId, Instant businessDate) {
        var result = returns.findByOwnerAt(ownerId, businessDate);
        var missing = result.funds().stream()
                .filter(fund -> fund.open() && fund.unrealizedPnl() == null)
                .map(PortfolioReturnQueryHandler.FundReturnResult::fundCode)
                .filter(java.util.Objects::nonNull).sorted().toList();
        BigDecimal holding = result.holdingAmount() == null ? result.funds().stream()
                .map(PortfolioReturnQueryHandler.FundReturnResult::holdingAmount)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)
                : result.holdingAmount();
        BigDecimal totalReturn = result.totalReturn() == null ? result.funds().stream()
                .map(PortfolioReturnQueryHandler.FundReturnResult::totalReturn)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)
                : result.totalReturn();
        Long id = snapshots.find(ownerId, businessDate).map(PortfolioReturnSnapshot::id).orElse(null);
        Instant now = clock.instant();
        snapshots.save(new PortfolioReturnSnapshot(id, ownerId, businessDate, result.investedAmount(),
                result.redeemedAmount(), result.feeAmount(), holding, result.realizedPnl(),
                result.unrealizedPnl(), totalReturn, missing.isEmpty(), String.join(",", missing), now));
    }
}
