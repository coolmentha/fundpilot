package com.fundpilot.backend.accounting.application.query.returnfacts;

import com.fundpilot.backend.accounting.domain.lot.Lot;
import com.fundpilot.backend.accounting.domain.lot.LotRedemption;
import com.fundpilot.backend.accounting.domain.lot.LotRepository;
import com.fundpilot.backend.accounting.domain.position.Position;
import com.fundpilot.backend.accounting.domain.position.PositionRepository;
import com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction;
import com.fundpilot.backend.accounting.domain.transaction.TransactionRepository;
import com.fundpilot.backend.accounting.domain.transaction.TransactionStatus;
import com.fundpilot.backend.sharedkernel.BusinessDay;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 由已确认账目和 FIFO lot 导出的组合收益事实，不承担行情估值。 */
@Service
@RequiredArgsConstructor
public class AccountingReturnQueryHandler {
    private static final MathContext MATH = MathContext.DECIMAL64;

    private final PositionRepository positions;
    private final TransactionRepository transactions;
    private final LotRepository lots;

    @Transactional(readOnly = true)
    public List<ReturnFact> findByOwner(long ownerId) {
        return findByOwner(ownerId, null);
    }

    @Transactional(readOnly = true)
    public List<ReturnFact> findByOwnerAt(long ownerId, Instant endExclusive) {
        return findByOwner(ownerId, java.util.Objects.requireNonNull(endExclusive));
    }

    private List<ReturnFact> findByOwner(long ownerId, Instant endExclusive) {
        List<Position> owned = positions.findByOwner(ownerId);
        List<Long> portfolioFundIds = owned.stream().map(Position::portfolioFundId).toList();
        List<LedgerTransaction> ledger = transactions.findByPortfolioFundIdsAndStatus(
                portfolioFundIds, TransactionStatus.CONFIRMED).stream()
                .filter(transaction -> endExclusive == null || BusinessDay.toDateLabel(
                        transaction.effectiveTradeDate(transaction.confirmTime())).isBefore(endExclusive))
                .toList();
        List<Lot> ownerLots = lots.findByPortfolioFundIds(portfolioFundIds);
        Map<Long, Lot> lotsById = ownerLots.stream().collect(Collectors.toMap(Lot::id, lot -> lot));
        Map<Long, List<LotRedemption>> redemptionsBySale = lots.findRedemptionsBySellTransactionIds(
                        ledger.stream().filter(transaction -> transaction.source().isSell())
                                .map(LedgerTransaction::id).toList())
                .stream().collect(Collectors.groupingBy(LotRedemption::sellTransactionId));
        Map<Long, List<LedgerTransaction>> ledgerByPortfolioFund = ledger.stream()
                .collect(Collectors.groupingBy(LedgerTransaction::portfolioFundId));
        Map<Long, Map<Long, BigDecimal>> costBySaleAndLot = redemptionsBySale.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().stream()
                        .collect(Collectors.groupingBy(LotRedemption::lotId, Collectors.reducing(BigDecimal.ZERO,
                                redemption -> redemption.sharesConsumed(), BigDecimal::add)))));

        return owned.stream().map(position -> summarize(position.portfolioFundId(),
                ledgerByPortfolioFund.getOrDefault(position.portfolioFundId(), List.of()), ownerLots,
                lotsById, costBySaleAndLot)).toList();
    }

    private static ReturnFact summarize(long portfolioFundId, List<LedgerTransaction> ledger, List<Lot> lots,
                                        Map<Long, Lot> lotsById,
                                        Map<Long, Map<Long, BigDecimal>> consumedBySaleAndLot) {
        Map<Long, BigDecimal> costByPurchase = lots.stream()
                .filter(lot -> lot.portfolioFundId() == portfolioFundId)
                .collect(Collectors.groupingBy(Lot::acquireTransactionId, Collectors.reducing(BigDecimal.ZERO,
                        lot -> lot.acquireShares().multiply(lot.acquireCostPerShare(), MATH), BigDecimal::add)));
        Totals totals = new Totals();
        for (LedgerTransaction transaction : ledger) {
            totals.fees = totals.fees.add(value(transaction.fee()));
            if (transaction.source().isBuy()) {
                BigDecimal cost = costByPurchase.getOrDefault(transaction.id(), value(transaction.amount()));
                totals.invested = totals.invested.add(cost);
                if (transaction.source() != com.fundpilot.backend.accounting.domain.transaction.TransactionSource.TRANSFER_IN) {
                    totals.externalInvested = totals.externalInvested.add(cost);
                }
            } else if (transaction.source().isSell()) {
                BigDecimal proceeds = value(transaction.amount());
                totals.redeemed = totals.redeemed.add(proceeds);
                if (transaction.source() != com.fundpilot.backend.accounting.domain.transaction.TransactionSource.TRANSFER_OUT) {
                    totals.externalRedeemed = totals.externalRedeemed.add(proceeds);
                }
                BigDecimal consumedShares = consumedBySaleAndLot.getOrDefault(transaction.id(), Map.of()).values()
                        .stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                boolean complete = transaction.shares() != null && consumedShares.compareTo(transaction.shares()) == 0;
                totals.realizedComplete &= complete;
                if (complete) {
                    BigDecimal costs = consumedBySaleAndLot.getOrDefault(transaction.id(), Map.of()).entrySet().stream()
                            .map(entry -> entry.getValue().multiply(lotsById.get(entry.getKey()).acquireCostPerShare(), MATH))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    totals.realized = totals.realized.add(proceeds.subtract(costs));
                }
            }
        }
        return new ReturnFact(portfolioFundId, totals.invested, totals.redeemed, totals.externalInvested,
                totals.externalRedeemed, totals.fees, totals.realizedComplete ? totals.realized : null,
                totals.realizedComplete);
    }

    private static BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record ReturnFact(long portfolioFundId, BigDecimal investedAmount, BigDecimal redeemedAmount,
                             BigDecimal externalInvestedAmount, BigDecimal externalRedeemedAmount,
                             BigDecimal feeAmount, BigDecimal realizedPnl, boolean realizedComplete) {
    }

    private static final class Totals {
        private BigDecimal invested = BigDecimal.ZERO;
        private BigDecimal redeemed = BigDecimal.ZERO;
        private BigDecimal externalInvested = BigDecimal.ZERO;
        private BigDecimal externalRedeemed = BigDecimal.ZERO;
        private BigDecimal fees = BigDecimal.ZERO;
        private BigDecimal realized = BigDecimal.ZERO;
        private boolean realizedComplete = true;
    }
}
