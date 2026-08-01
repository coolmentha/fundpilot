package com.fundpilot.backend.accounting.application.command.positiontracking;

import com.fundpilot.backend.accounting.application.event.position.PositionCleared;
import com.fundpilot.backend.accounting.application.event.position.PositionOpened;
import com.fundpilot.backend.accounting.application.gateway.transactionledger.LedgerEventGateway;
import com.fundpilot.backend.accounting.domain.ledgerreplay.LedgerReplay;
import com.fundpilot.backend.accounting.domain.position.Position;
import com.fundpilot.backend.accounting.domain.position.PositionRepository;
import com.fundpilot.backend.accounting.domain.position.PositionTransition;
import com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction;
import com.fundpilot.backend.accounting.domain.transaction.TransactionRepository;
import com.fundpilot.backend.accounting.domain.transaction.TransactionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * 持仓校准用例。交易确认/撤销后按 CONFIRMED 事实账本统一重算持仓状态、建仓时间与成本单价，
 * 并把状态迁移转换为 {@code PositionOpened}/{@code PositionCleared} 集成事件。
 */
@Service
@RequiredArgsConstructor
public class PositionCommandHandler {

    private final TransactionRepository transactions;
    private final PositionRepository positions;
    private final LedgerEventGateway events;
    private final Clock clock;

    /** 按账本重放校准持仓；返回校准后的持仓事实。 */
    @Transactional
    public PositionResult reconcile(long portfolioFundId, long ownerId) {
        List<LedgerTransaction> confirmed =
                transactions.findByPortfolioFundAndStatus(portfolioFundId, TransactionStatus.CONFIRMED);
        Position position = loadOrCreate(portfolioFundId, ownerId);
        BigDecimal netShares = LedgerReplay.netShares(confirmed);
        Optional<PositionTransition> transition = position.reconcile(!confirmed.isEmpty(), netShares,
                LedgerReplay.latestInflowAt(confirmed, clock.instant()));
        Position saved = positions.save(position);
        transition.ifPresent(change -> publish(change, saved));
        return PositionResult.from(saved, netShares);
    }

    /** 买入确认后加权更新成本单价，再校准状态。 */
    @Transactional
    public PositionResult applyPurchase(long portfolioFundId, long ownerId, BigDecimal acquiredShares,
                                        BigDecimal effectiveAmount) {
        Position position = loadOrCreate(portfolioFundId, ownerId);
        List<LedgerTransaction> confirmed =
                transactions.findByPortfolioFundAndStatus(portfolioFundId, TransactionStatus.CONFIRMED);
        BigDecimal netShares = LedgerReplay.netShares(confirmed);
        position.applyPurchase(netShares, acquiredShares, effectiveAmount,
                LedgerReplay.untrackedShares(confirmed));
        Optional<PositionTransition> transition = position.reconcile(!confirmed.isEmpty(), netShares,
                LedgerReplay.latestInflowAt(confirmed, clock.instant()));
        Position saved = positions.save(position);
        transition.ifPresent(change -> publish(change, saved));
        return PositionResult.from(saved, netShares);
    }

    /** 期初持仓按用户输入的成本单价直接建立，不做加权。 */
    @Transactional
    public PositionResult applyExistingPosition(long portfolioFundId, long ownerId,
                                                BigDecimal costPerShare, java.time.Instant openedAt) {
        Position position = loadOrCreate(portfolioFundId, ownerId);
        position.applyExistingPosition(costPerShare, openedAt);
        positions.save(position);
        return reconcile(portfolioFundId, ownerId);
    }

    /** 作废组合基金后移除可重建持仓投影；重复事件保持无操作。 */
    @Transactional
    public void removeVoidedProjection(long portfolioFundId) {
        positions.removeByPortfolioFund(portfolioFundId);
    }

    private Position loadOrCreate(long portfolioFundId, long ownerId) {
        return positions.findByPortfolioFund(portfolioFundId)
                .orElseGet(() -> Position.empty(portfolioFundId, ownerId));
    }

    private void publish(PositionTransition change, Position saved) {
        long positionVersion = saved.version();
        if (change.opened()) {
            events.publishPositionOpened(new PositionOpened(change.portfolioFundId(), change.ownerId(),
                    saved.openedAt(), positionVersion, clock.instant()));
        } else if (change.cleared()) {
            events.publishPositionCleared(new PositionCleared(change.portfolioFundId(),
                    change.ownerId(), positionVersion, clock.instant()));
        }
    }

    public record PositionResult(long portfolioFundId, long ownerId, String status,
                                 java.time.Instant openedAt, BigDecimal costPerShare,
                                 BigDecimal holdingShares) {
        static PositionResult from(Position position, BigDecimal holdingShares) {
            return new PositionResult(position.portfolioFundId(), position.ownerId(),
                    position.status().name(), position.openedAt(), position.costPerShare(),
                    holdingShares);
        }
    }
}
