package com.fundpilot.backend.accounting.domain.ledgerreplay;

import com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction;
import com.fundpilot.backend.accounting.domain.transaction.ShareScale;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
            }
        }
        return untracked;
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
