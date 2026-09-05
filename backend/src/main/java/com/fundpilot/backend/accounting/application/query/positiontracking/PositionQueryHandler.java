package com.fundpilot.backend.accounting.application.query.positiontracking;

import com.fundpilot.backend.accounting.domain.position.Position;
import com.fundpilot.backend.accounting.domain.position.PositionRepository;
import com.fundpilot.backend.accounting.domain.lot.LotRepository;
import com.fundpilot.backend.accounting.domain.ledgerreplay.LedgerReplay;
import com.fundpilot.backend.accounting.domain.transaction.TransactionRepository;
import com.fundpilot.backend.accounting.domain.transaction.TransactionStatus;
import com.fundpilot.backend.sharedkernel.BusinessDay;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Accounting 持仓事实的只读用例。 */
@Service
@RequiredArgsConstructor
public class PositionQueryHandler {
    private final PositionRepository positions;
    private final TransactionRepository transactions;
    private final LotRepository lots;

    @Transactional(readOnly = true)
    public List<PositionResult> findByOwner(long ownerId) {
        List<Position> owned = positions.findByOwner(ownerId);
        Map<Long, BigDecimal> shares = confirmedShares(owned.stream().map(Position::portfolioFundId).toList());
        return owned.stream().map(position -> PositionResult.from(position,
                shares.getOrDefault(position.portfolioFundId(), BigDecimal.ZERO))).toList();
    }

    @Transactional(readOnly = true)
    public List<PositionResult> findByOwnerAt(long ownerId, Instant endExclusive) {
        List<Position> owned = positions.findByOwner(ownerId);
        List<com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction> ledger =
                transactions.findByPortfolioFundIdsAndStatus(
                                owned.stream().map(Position::portfolioFundId).toList(), TransactionStatus.CONFIRMED)
                        .stream().filter(transaction -> BusinessDay.toDateLabel(
                                transaction.effectiveTradeDate(transaction.confirmTime()))
                                .isBefore(endExclusive)).toList();
        Map<Long, List<com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction>> byFund = ledger.stream()
                .collect(Collectors.groupingBy(
                        com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction::portfolioFundId));
        return owned.stream().map(position -> historical(position,
                byFund.getOrDefault(position.portfolioFundId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public Optional<PositionResult> findOwned(long ownerId, long portfolioFundId) {
        return positions.findByPortfolioFund(portfolioFundId)
                .filter(position -> position.ownerId() == ownerId)
                .map(position -> PositionResult.from(position,
                        confirmedShares(List.of(portfolioFundId)).getOrDefault(portfolioFundId, BigDecimal.ZERO)));
    }

    @Transactional(readOnly = true)
    public List<OpenLotResult> findOpenLots(long ownerId, long portfolioFundId) {
        if (positions.findByPortfolioFund(portfolioFundId)
                .filter(position -> position.ownerId() == ownerId).isEmpty()) {
            return List.of();
        }
        return lots.findOpenLotsOrderByAcquireDate(portfolioFundId).stream()
                .map(lot -> new OpenLotResult(lot.acquireDate(), lot.remainingShares()))
                .toList();
    }

    public record PositionResult(long portfolioFundId, long ownerId, String status, Instant openedAt,
                                 BigDecimal costPerShare, BigDecimal confirmedShares) {
        private static PositionResult from(Position position, BigDecimal confirmedShares) {
            return new PositionResult(position.portfolioFundId(), position.ownerId(), position.status().name(),
                    position.openedAt(), position.costPerShare(), confirmedShares);
        }
    }

    public record OpenLotResult(Instant acquireDate, BigDecimal remainingShares) {
    }

    private Map<Long, BigDecimal> confirmedShares(Collection<Long> portfolioFundIds) {
        return transactions.aggregateConfirmedShares(portfolioFundIds).stream()
                .collect(Collectors.toMap(TransactionRepository.HoldingShares::portfolioFundId,
                        TransactionRepository.HoldingShares::holdingShares));
    }

    private static PositionResult historical(Position position,
            List<com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction> ledger) {
        BigDecimal shares = LedgerReplay.netShares(ledger);
        String status = ledger.isEmpty() ? "EMPTY" : shares.signum() > 0 ? "OPEN" : "CLEARED";
        return new PositionResult(position.portfolioFundId(), position.ownerId(), status,
                LedgerReplay.latestInflowAt(ledger, null),
                LedgerReplay.replayHistoricalCostPerShare(ledger).orElse(null), shares);
    }
}
