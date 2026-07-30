package com.fundpilot.backend.accounting.domain.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * 账目流水聚合根。承载一笔交易从录入到确认/撤销的全部不变量。
 *
 * <p>确认时固化 {@code nav} 单位净值快照，不引用可变行情实体；金额与份额的另一侧由
 * 应用层按费率与净值算好后经 {@link #confirm} 一次性写入，保证聚合内状态自洽。
 */
public final class LedgerTransaction {

    private final Long id;
    private final long portfolioFundId;
    private final long ownerId;
    private final TransactionSource source;
    private TransactionStatus status;
    private BigDecimal amount;
    private BigDecimal shares;
    private BigDecimal nav;
    private BigDecimal fee;
    private BigDecimal feeRate;
    private Instant tradeDate;
    private Instant confirmTime;
    private Instant cancelTime;
    private final Instant createdDate;
    private Long relatedTransactionId;
    private final Long signalLogId;
    private final Long dcaPlanId;
    private final Long disciplineAdviceId;
    private final Long investmentPlanId;

    private LedgerTransaction(Long id, long portfolioFundId, long ownerId, TransactionSource source,
                              TransactionStatus status, BigDecimal amount, BigDecimal shares,
                              BigDecimal nav, BigDecimal fee, BigDecimal feeRate, Instant tradeDate,
                              Instant confirmTime, Instant cancelTime, Instant createdDate,
                              Long relatedTransactionId, Long signalLogId, Long dcaPlanId,
                              Long disciplineAdviceId, Long investmentPlanId) {
        this.id = id;
        this.portfolioFundId = requirePositive(portfolioFundId, "组合基金 ID");
        this.ownerId = requirePositive(ownerId, "用户 ID");
        this.source = Objects.requireNonNull(source, "交易来源不能为空");
        this.status = Objects.requireNonNull(status, "交易状态不能为空");
        this.amount = amount;
        this.shares = ShareScale.normalize(shares);
        this.nav = nav;
        this.fee = fee;
        this.feeRate = feeRate;
        this.tradeDate = tradeDate;
        this.confirmTime = confirmTime;
        this.cancelTime = cancelTime;
        this.createdDate = createdDate;
        this.relatedTransactionId = relatedTransactionId;
        this.signalLogId = signalLogId;
        this.dcaPlanId = dcaPlanId;
        this.disciplineAdviceId = disciplineAdviceId;
        this.investmentPlanId = investmentPlanId;
    }

    /** 录入一笔待确认流水；买入类需金额，卖出类需份额。 */
    public static LedgerTransaction placePending(long portfolioFundId, long ownerId,
                                                 TransactionSource source, BigDecimal amount,
                                                 BigDecimal shares, Instant tradeDate,
                                                 Long signalLogId, Long dcaPlanId) {
        return placePending(portfolioFundId, ownerId, source, amount, shares, tradeDate,
                signalLogId, dcaPlanId, null);
    }

    /** 由 Discipline 建议回应创建的待确认流水，使用新建议 ID 作为幂等来源引用。 */
    public static LedgerTransaction placePending(long portfolioFundId, long ownerId,
                                                 TransactionSource source, BigDecimal amount,
                                                 BigDecimal shares, Instant tradeDate,
                                                 Long signalLogId, Long dcaPlanId,
                                                 Long disciplineAdviceId) {
        return placePending(portfolioFundId, ownerId, source, amount, shares, tradeDate, signalLogId,
                dcaPlanId, disciplineAdviceId, null);
    }

    /** 由 InvestmentPlan 执行日生成的待确认流水。 */
    public static LedgerTransaction placePending(long portfolioFundId, long ownerId,
                                                 TransactionSource source, BigDecimal amount,
                                                 BigDecimal shares, Instant tradeDate,
                                                 Long signalLogId, Long dcaPlanId,
                                                 Long disciplineAdviceId, Long investmentPlanId) {
        Objects.requireNonNull(source, "交易来源不能为空");
        Objects.requireNonNull(tradeDate, "交易发生时间不能为空");
        if (source.isAdjustment()) {
            throw new IllegalArgumentException("调整交易录入即确认，不能作为待确认流水");
        }
        requireInput(source, amount, shares);
        return new LedgerTransaction(null, portfolioFundId, ownerId, source, TransactionStatus.PENDING,
                amount, shares, null, null, null, tradeDate, null, null, null, null,
                signalLogId, dcaPlanId, disciplineAdviceId, investmentPlanId);
    }

    /** 录入一笔调整流水，创建即确认，不计净值与费用。 */
    public static LedgerTransaction recordAdjustment(long portfolioFundId, long ownerId,
                                                     TransactionSource source, BigDecimal shares,
                                                     Instant tradeDate, Instant confirmedAt) {
        if (source == null || !source.isAdjustment()) {
            throw new IllegalArgumentException("调整交易来源必须为 ADJUST_IN 或 ADJUST_OUT");
        }
        BigDecimal normalized = ShareScale.normalize(shares);
        if (normalized == null || normalized.signum() <= 0) {
            throw new IllegalArgumentException(source + " 需填正数份额");
        }
        return new LedgerTransaction(null, portfolioFundId, ownerId, source, TransactionStatus.CONFIRMED,
                null, normalized, null, null, null, Objects.requireNonNull(tradeDate, "交易发生时间不能为空"),
                Objects.requireNonNull(confirmedAt, "确认时间不能为空"), null, null, null, null, null, null, null);
    }

    /** 录入一笔期初持仓流水，创建即确认，按用户输入成本建立后续 FIFO 所需 lot。 */
    public static LedgerTransaction recordExistingPosition(long portfolioFundId, long ownerId,
                                                           BigDecimal shares, BigDecimal navSnapshot,
                                                           Instant tradeDate, Instant confirmedAt) {
        BigDecimal normalized = ShareScale.normalize(shares);
        if (normalized == null || normalized.signum() <= 0) {
            throw new IllegalArgumentException("初始持仓需填正数份额");
        }
        if (navSnapshot == null || navSnapshot.signum() <= 0) {
            throw new IllegalArgumentException("初始持仓净值必须为正数");
        }
        return new LedgerTransaction(null, portfolioFundId, ownerId, TransactionSource.INCREASE,
                TransactionStatus.CONFIRMED, normalized.multiply(navSnapshot), normalized, navSnapshot, null, null,
                Objects.requireNonNull(tradeDate, "交易发生时间不能为空"),
                Objects.requireNonNull(confirmedAt, "确认时间不能为空"), null, null, null, null, null, null, null);
    }

    public static LedgerTransaction rehydrate(long id, long portfolioFundId, long ownerId,
                                              TransactionSource source, TransactionStatus status,
                                              BigDecimal amount, BigDecimal shares, BigDecimal nav,
                                              BigDecimal fee, BigDecimal feeRate, Instant tradeDate,
                                              Instant confirmTime, Instant cancelTime, Instant createdDate,
                                              Long relatedTransactionId, Long signalLogId, Long dcaPlanId,
                                              Long disciplineAdviceId, Long investmentPlanId) {
        return new LedgerTransaction(requirePositive(id, "交易 ID"), portfolioFundId, ownerId, source,
                status, amount, shares, nav, fee, feeRate, tradeDate, confirmTime, cancelTime,
                createdDate, relatedTransactionId, signalLogId, dcaPlanId, disciplineAdviceId, investmentPlanId);
    }

    /**
     * 确认流水并固化净值、份额/金额另一侧与费用快照。
     *
     * @param settlement 应用层按费率与净值算出的结算结果
     */
    public void confirm(Settlement settlement, Instant confirmedAt) {
        requirePending("确认");
        Objects.requireNonNull(settlement, "结算结果不能为空");
        if (settlement.nav() == null || settlement.nav().signum() <= 0) {
            throw new IllegalArgumentException("确认净值必须为正数");
        }
        this.nav = settlement.nav();
        this.amount = settlement.amount();
        this.shares = ShareScale.normalize(settlement.shares());
        this.fee = settlement.fee();
        this.feeRate = settlement.feeRate();
        this.confirmTime = Objects.requireNonNull(confirmedAt, "确认时间不能为空");
        this.status = TransactionStatus.CONFIRMED;
    }

    public void cancel(Instant cancelledAt) {
        requirePending("撤销");
        this.cancelTime = Objects.requireNonNull(cancelledAt, "撤销时间不能为空");
        this.status = TransactionStatus.CANCELLED;
    }

    /** 修改待确认流水的业务输入；来源、组合基金与关联关系保持不变。 */
    public void reviseInput(BigDecimal newAmount, BigDecimal newShares, Instant newTradeDate) {
        requirePending("修改");
        if (source.isAdjustment()) {
            throw new IllegalStateException("调整交易创建即确认，不可修改");
        }
        Objects.requireNonNull(newTradeDate, "交易发生时间不能为空");
        requireInput(source, newAmount, newShares);
        if (source.isBuy()) {
            this.amount = newAmount;
        } else {
            this.shares = ShareScale.normalize(newShares);
        }
        this.tradeDate = newTradeDate;
    }

    /** 转换双腿互指；转入腿的金额在转出腿确认后回填。 */
    public void linkRelated(Long otherTransactionId) {
        this.relatedTransactionId = otherTransactionId;
    }

    /** 转入腿承接转出腿的净额，作为其确认输入。 */
    public void inheritConversionAmount(BigDecimal outLegNetAmount) {
        if (source != TransactionSource.TRANSFER_IN) {
            throw new IllegalStateException("只有转入腿可以承接转出净额");
        }
        requirePending("承接转换金额");
        if (outLegNetAmount == null || outLegNetAmount.signum() <= 0) {
            throw new IllegalArgumentException("转出净额必须为正数");
        }
        this.amount = outLegNetAmount;
    }

    /** 该流水对持仓份额的有向贡献；未填份额记 0。 */
    public BigDecimal signedShares() {
        return ShareScale.normalizeOrZero(shares).multiply(source.direction());
    }

    /** 业务交易时间；缺失时退回创建时间，再退回给定兜底值。 */
    public Instant effectiveTradeDate(Instant fallback) {
        if (tradeDate != null) {
            return tradeDate;
        }
        return createdDate != null ? createdDate : fallback;
    }

    /** 确认所需的业务输入是否齐备。 */
    public boolean hasRequiredInput() {
        if (source.isBuy()) {
            return amount != null && amount.signum() > 0;
        }
        if (source.isSell()) {
            return shares != null && shares.signum() > 0;
        }
        return false;
    }

    private void requirePending(String action) {
        if (status == TransactionStatus.CONFIRMED) {
            throw new IllegalStateException("已确认交易不可" + action);
        }
        if (status == TransactionStatus.CANCELLED) {
            throw new IllegalStateException("已撤销交易不可" + action);
        }
    }

    private static void requireInput(TransactionSource source, BigDecimal amount, BigDecimal shares) {
        if (source.isBuy() && (amount == null || amount.signum() <= 0)) {
            throw new IllegalArgumentException(source + " 需填正数金额");
        }
        if (source.isSell()) {
            BigDecimal normalized = ShareScale.normalize(shares);
            if (normalized == null || normalized.signum() <= 0) {
                throw new IllegalArgumentException(source + " 需填正数份额");
            }
        }
    }

    private static long requirePositive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + "必须为正数");
        }
        return value;
    }

    /** 确认结算结果：净值快照与费用拆分。 */
    public record Settlement(BigDecimal nav, BigDecimal amount, BigDecimal shares,
                             BigDecimal fee, BigDecimal feeRate) {
    }

    public Long id() { return id; }
    public long portfolioFundId() { return portfolioFundId; }
    public long ownerId() { return ownerId; }
    public TransactionSource source() { return source; }
    public TransactionStatus status() { return status; }
    public BigDecimal amount() { return amount; }
    public BigDecimal shares() { return shares; }
    public BigDecimal nav() { return nav; }
    public BigDecimal fee() { return fee; }
    public BigDecimal feeRate() { return feeRate; }
    public Instant tradeDate() { return tradeDate; }
    public Instant confirmTime() { return confirmTime; }
    public Instant cancelTime() { return cancelTime; }
    public Instant createdDate() { return createdDate; }
    public Long relatedTransactionId() { return relatedTransactionId; }
    public Long signalLogId() { return signalLogId; }
    public Long dcaPlanId() { return dcaPlanId; }
    public Long disciplineAdviceId() { return disciplineAdviceId; }
    public Long investmentPlanId() { return investmentPlanId; }
}
