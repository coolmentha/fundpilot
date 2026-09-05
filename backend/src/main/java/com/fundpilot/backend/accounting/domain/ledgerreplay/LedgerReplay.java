package com.fundpilot.backend.accounting.domain.ledgerreplay;

import com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction;
import com.fundpilot.backend.accounting.domain.transaction.ShareScale;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 账本重放：把一组流水折算为持仓事实。跨 transaction 与 lot 两个聚合，是账目模块的核心业务规则，
 * 因此独立成职责包而不塞进任一聚合。
 *
 * <p>金额永远实时算，账本只存份额（CONTEXT.md 硬性原则）。转换双腿各自独立计入，
 * 一腿对本组合基金是转入（+）另一腿是转出（−），方向已天然抵消。
 */
public final class LedgerReplay {

    private LedgerReplay() {
    }

    /** 净份额 = Σ 份额 × 方向。调用方负责只传入同一状态的流水。 */
    public static BigDecimal netShares(List<LedgerTransaction> transactions) {
        BigDecimal sum = BigDecimal.ZERO;
        for (LedgerTransaction transaction : transactions) {
            sum = sum.add(transaction.signedShares());
        }
        return sum;
    }

    /**
     * 当前未由收费 lot 跟踪的事实份额。按 CONFIRMED 账本重放 FIFO：只有 {@code ADJUST_IN}
     * 产生未跟踪份额；卖出与 {@code ADJUST_OUT} 都先消耗普通买入份额，再消耗未跟踪份额。
     */
    public static BigDecimal untrackedShares(List<LedgerTransaction> confirmed) {
        BigDecimal tracked = BigDecimal.ZERO;
        BigDecimal untracked = BigDecimal.ZERO;
        for (LedgerTransaction transaction : inLedgerOrder(confirmed)) {
            BigDecimal shares = ShareScale.normalizeOrZero(transaction.shares());
            switch (transaction.source()) {
                case INCREASE, TRANSFER_IN, INVEST -> tracked = tracked.add(shares);
                case ADJUST_IN -> untracked = untracked.add(shares);
                case DECREASE, TRANSFER_OUT, ADJUST_OUT -> {
                    BigDecimal trackedConsumed = tracked.min(shares);
                    tracked = tracked.subtract(trackedConsumed);
                    untracked = untracked.subtract(shares.subtract(trackedConsumed)).max(BigDecimal.ZERO);
                }
                case COST_BASIS_RESET -> { /* 成本事实不改变份额或 lot 跟踪。 */ }
            }
        }
        return untracked;
    }

    /**
     * 仅对包含成本基准重置的账本重放当前成本；没有重置记录时返回 empty，保留存量持仓的旧增量规则。
     * 重置前的买入成本被丢弃，重置后的 ADJUST_IN 保留零成本份额语义。
     */
    public static Optional<BigDecimal> replayCostPerShare(List<LedgerTransaction> confirmed) {
        return replayCostPerShare(confirmed, true);
    }

    /** 历史观察点按当时账本重放成本，不读取后来更新的当前持仓。 */
    public static Optional<BigDecimal> replayHistoricalCostPerShare(List<LedgerTransaction> confirmed) {
        return replayCostPerShare(confirmed, false);
    }

    private static Optional<BigDecimal> replayCostPerShare(List<LedgerTransaction> confirmed,
                                                            boolean requireReset) {
        boolean resetSeen = false;
        BigDecimal shares = BigDecimal.ZERO;
        BigDecimal untracked = BigDecimal.ZERO;
        BigDecimal costPerShare = null;
        for (LedgerTransaction transaction : inLedgerOrder(confirmed)) {
            BigDecimal transactionShares = ShareScale.normalizeOrZero(transaction.shares());
            switch (transaction.source()) {
                case COST_BASIS_RESET -> {
                    if (transaction.amount() == null || transaction.amount().signum() <= 0
                            || transactionShares.signum() <= 0) {
                        throw new IllegalStateException("成本基准重置缺少有效成本快照 tx=" + transaction.id());
                    }
                    costPerShare = LedgerTransaction.costPerShareFromStoredAmount(
                            transaction.amount(), transactionShares);
                    // 快照反映写入时已确认份额；晚确认但业务日期更早的流水已经重放到 shares，不能丢掉。
                    shares = shares.signum() > 0 ? shares : transactionShares;
                    untracked = BigDecimal.ZERO;
                    resetSeen = true;
                }
                case INCREASE, TRANSFER_IN, INVEST -> {
                    BigDecimal previousShares = shares;
                    if (previousShares.signum() <= 0 || costPerShare == null) {
                        costPerShare = transaction.amount() == null || transactionShares.signum() <= 0
                                ? null : transaction.amount().divide(
                                previousShares.signum() > 0 ? previousShares.add(transactionShares) : transactionShares,
                                java.math.MathContext.DECIMAL64);
                    } else {
                        BigDecimal trackedPrevious = previousShares.subtract(untracked).max(BigDecimal.ZERO);
                        costPerShare = costPerShare.multiply(trackedPrevious,
                                java.math.MathContext.DECIMAL64).add(transaction.amount())
                                .divide(previousShares.add(transactionShares),
                                        java.math.MathContext.DECIMAL64);
                    }
                    shares = previousShares.add(transactionShares);
                }
                case ADJUST_IN -> {
                    shares = shares.add(transactionShares);
                    untracked = untracked.add(transactionShares);
                }
                case DECREASE, TRANSFER_OUT, ADJUST_OUT -> {
                    shares = shares.subtract(transactionShares).max(BigDecimal.ZERO);
                    BigDecimal trackedConsumed = shares.add(transactionShares).subtract(untracked)
                            .max(BigDecimal.ZERO).min(transactionShares);
                    untracked = untracked.subtract(transactionShares.subtract(trackedConsumed))
                            .max(BigDecimal.ZERO);
                }
            }
        }
        return !requireReset || resetSeen ? Optional.ofNullable(costPerShare) : Optional.empty();
    }

    /** 最近一笔正向 CONFIRMED 流水的交易时间，用于重建建仓时间。 */
    public static Instant latestInflowAt(List<LedgerTransaction> confirmed, Instant fallback) {
        return confirmed.stream()
                .filter(transaction -> transaction.source().direction().signum() > 0)
                .map(transaction -> transaction.effectiveTradeDate(fallback))
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
    }

    /** 账本顺序：按交易发生时间升序，同刻按 ID 升序，保证重放结果稳定。 */
    private static List<LedgerTransaction> inLedgerOrder(List<LedgerTransaction> transactions) {
        List<LedgerTransaction> ordered = new ArrayList<>(transactions);
        ordered.sort(Comparator
                .comparing((LedgerTransaction transaction) ->
                                transaction.effectiveTradeDate(transaction.confirmTime()),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(LedgerTransaction::id,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        return ordered;
    }
}
