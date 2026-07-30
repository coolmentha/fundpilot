package com.fundpilot.backend.accounting.domain.lot;

import com.fundpilot.backend.accounting.domain.transaction.ShareScale;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * 买入 lot（税 lot）。每笔确认的买入建一行，卖出时按 {@link #acquireDate} 升序 FIFO 消耗，
 * 供赎回费按持有期（卖出交易日 − 买入交易日）匹配阶梯。
 */
public final class Lot {

    private final Long id;
    private final long portfolioFundId;
    private final long acquireTransactionId;
    private final Instant acquireDate;
    private final BigDecimal acquireShares;
    private BigDecimal remainingShares;
    private final BigDecimal acquireCostPerShare;

    private Lot(Long id, long portfolioFundId, long acquireTransactionId, Instant acquireDate,
                BigDecimal acquireShares, BigDecimal remainingShares, BigDecimal acquireCostPerShare) {
        this.id = id;
        this.portfolioFundId = portfolioFundId;
        this.acquireTransactionId = acquireTransactionId;
        this.acquireDate = Objects.requireNonNull(acquireDate, "买入交易时间不能为空");
        this.acquireShares = Objects.requireNonNull(acquireShares, "买入份额不能为空");
        this.remainingShares = Objects.requireNonNull(remainingShares, "剩余份额不能为空");
        this.acquireCostPerShare = Objects.requireNonNull(acquireCostPerShare, "买入成本单价不能为空");
    }

    public static Lot open(long portfolioFundId, long acquireTransactionId, Instant acquireDate,
                           BigDecimal acquireShares, BigDecimal acquireCostPerShare) {
        BigDecimal shares = ShareScale.normalize(acquireShares);
        if (shares == null || shares.signum() <= 0) {
            throw new IllegalArgumentException("买入份额必须为正数");
        }
        return new Lot(null, portfolioFundId, acquireTransactionId, acquireDate, shares, shares,
                acquireCostPerShare);
    }

    public static Lot rehydrate(long id, long portfolioFundId, long acquireTransactionId,
                                Instant acquireDate, BigDecimal acquireShares,
                                BigDecimal remainingShares, BigDecimal acquireCostPerShare) {
        return new Lot(id, portfolioFundId, acquireTransactionId, acquireDate, acquireShares,
                remainingShares, acquireCostPerShare);
    }

    /** 消耗剩余份额，返回实际消耗量（不超过剩余）。 */
    public BigDecimal consume(BigDecimal requestedShares) {
        BigDecimal consumed = remainingShares.min(Objects.requireNonNull(requestedShares, "消耗份额不能为空"));
        if (consumed.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        remainingShares = remainingShares.subtract(consumed);
        return consumed;
    }

    public boolean isOpen() {
        return remainingShares.signum() > 0;
    }

    public Long id() { return id; }
    public long portfolioFundId() { return portfolioFundId; }
    public long acquireTransactionId() { return acquireTransactionId; }
    public Instant acquireDate() { return acquireDate; }
    public BigDecimal acquireShares() { return acquireShares; }
    public BigDecimal remainingShares() { return remainingShares; }
    public BigDecimal acquireCostPerShare() { return acquireCostPerShare; }
}
